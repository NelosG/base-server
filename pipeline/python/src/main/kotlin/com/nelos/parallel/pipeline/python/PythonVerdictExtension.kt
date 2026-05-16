package com.nelos.parallel.pipeline.python

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.pipeline.commons.enums.ScriptType
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.commons.extension.JudgeContext
import com.nelos.parallel.pipeline.commons.extension.JudgeResult
import com.nelos.parallel.pipeline.commons.extension.ScriptVerdictExtension
import com.nelos.parallel.pipeline.commons.service.EvaluationContext
import com.nelos.parallel.pipeline.commons.service.SubmissionVerdict
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Runs instructor-authored Python verdict scripts as a subprocess. The
 * orchestrator marshals a [JudgeContext] to JSON, pipes it into the script
 * on stdin, and parses a [JudgeResult] back from stdout. Identity-related
 * data is omitted from the JSON the script sees.
 *
 * Robustness over elegance - any of {python3 missing, non-zero exit code,
 * malformed stdout, timeout} falls back to the baseline verdict and tags
 * the `reason` so the instructor can see what went wrong. We deliberately
 * do not crash the orchestrator over a broken instructor script.
 *
 * Python executable detection: probes `python3` first, then `python` (the
 * usual fallback on Windows installs). Result is cached for the lifetime
 * of the JVM; if neither works at startup, every script run will record
 * the missing-runtime reason instead of attempting to spawn anything.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.pythonVerdictExtension")
class PythonVerdictExtension(
    private val objectMapper: ObjectMapper,
) : ScriptVerdictExtension {

    override val scriptType: ScriptType = ScriptType.PYTHON

    /**
     * Lazily resolves which python executable is on PATH. Returns the
     * command name to spawn, or null if no python is reachable. Each
     * candidate is probed once with `<cmd> --version` and a 3-second
     * budget - we don't want to hang application boot on a misconfigured
     * environment.
     */
    private val pythonExecutable: String? by lazy {
        val candidates = listOf("python3", "python")
        candidates.firstOrNull { probe(it) }
            .also { picked ->
                if (picked != null) {
                    LOG.info("Python verdict-script runtime resolved to '{}' on PATH", picked)
                } else {
                    LOG.warn(
                        "Neither python3 nor python found on PATH - python verdict scripts will fall back to baseline",
                    )
                }
            }
    }

    override fun apply(context: EvaluationContext, current: SubmissionVerdict): SubmissionVerdict {
        val script = context.script ?: return current
        if (script.type != scriptType) return current

        val python = pythonExecutable ?: return current.copy(
            reason = "Python interpreter not found on PATH - using baseline verdict",
        )

        val judgeContext = JudgeContext(context.result, current)
        val ctxJson = try {
            objectMapper.writeValueAsString(judgeContext)
        } catch (e: Exception) {
            LOG.warn("Failed to serialise JudgeContext for python script: {}", e.message)
            return current.copy(reason = "Could not serialise JudgeContext for python script - used baseline")
        }

        return try {
            val raw = runScript(python, script.source, ctxJson, script.timeoutMs)
            val verdict = parseVerdict(raw)
            translate(verdict, current)
        } catch (e: TimeoutCancellation) {
            LOG.warn("Python verdict script timed out after {} ms", script.timeoutMs)
            current.copy(reason = "Python script timed out after ${script.timeoutMs} ms - used baseline")
        } catch (e: ProcessFailure) {
            LOG.warn("Python verdict script failed: exit={}, stderr={}", e.exitCode, e.stderrPreview)
            current.copy(
                reason = "Python script exited with code ${e.exitCode}" +
                        (if (e.stderrPreview.isNotBlank()) ": ${e.stderrPreview}" else "") +
                        " - used baseline",
            )
        } catch (e: Exception) {
            LOG.warn("Python verdict script error: {}", e.message, e)
            current.copy(reason = "Python script error: ${e.message ?: e::class.simpleName} - used baseline")
        }
    }

    private fun translate(judge: JudgeResult, baseline: SubmissionVerdict): SubmissionVerdict =
        SubmissionVerdict(
            submissionStatus = if (judge.pass) SubmissionStatus.COMPLETED else SubmissionStatus.FAILED,
            jobStatus = if (judge.pass) JobStatus.SUCCESS else JobStatus.FAILED,
            summary = judge.summary ?: baseline.summary,
            reason = judge.reason,
        )

    /**
     * Spawn `python <tempfile>`, feed [ctxJson] over stdin, read stdout
     * until the process exits, and return the raw bytes-as-text. The
     * script lives on disk only for the lifetime of the call - we delete
     * it in the finally block even if the process is killed.
     */
    private fun runScript(python: String, source: String, ctxJson: String, timeoutMs: Long): String {
        val scriptFile: Path = Files.createTempFile("prl-judge-", ".py")
        try {
            Files.writeString(scriptFile, source, StandardCharsets.UTF_8)
            val process = ProcessBuilder(python, scriptFile.toString())
                .redirectErrorStream(false)
                .start()
            // Drain stderr in parallel so a chatty script can't deadlock by
            // filling the OS pipe buffer while we're waiting on stdout.
            val stderrPump = StreamPump(process.errorStream)
            stderrPump.start()

            try {
                process.outputStream.use { it.write(ctxJson.toByteArray(StandardCharsets.UTF_8)) }
            } catch (e: IOException) {
                // Script may have closed stdin early (perfectly legal). Don't
                // treat that as a failure - let exit code be the source of truth.
                LOG.debug("Python script closed stdin before we finished writing: {}", e.message)
            }

            val finished = process.waitFor(timeoutMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw TimeoutCancellation()
            }

            val stdout = process.inputStream.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
            stderrPump.join(500)
            val stderr = stderrPump.captured()

            val exit = process.exitValue()
            if (exit != 0) {
                throw ProcessFailure(exitCode = exit, stderrPreview = stderr.take(STDERR_PREVIEW_LEN))
            }
            return stdout
        } finally {
            runCatching { Files.deleteIfExists(scriptFile) }
        }
    }

    private fun parseVerdict(raw: String): JudgeResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) error("Python script produced no stdout")
        // Use last non-blank line as the verdict - instructor scripts often
        // print warm-up logs before the final JSON object, and json.dumps
        // emits a single line.
        val lastJsonLine = trimmed.lineSequence().filter { it.isNotBlank() }.last().trim()
        return objectMapper.readValue(lastJsonLine, JudgeResult::class.java)
    }

    private fun probe(command: String): Boolean = try {
        val p = ProcessBuilder(command, "--version").redirectErrorStream(true).start()
        if (!p.waitFor(3, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            false
        } else {
            p.exitValue() == 0
        }
    } catch (e: Exception) {
        LOG.debug("Python probe '{}' failed: {}", command, e.message)
        false
    }

    /**
     * Background reader for the child's stderr - drains the pipe so the
     * child can't block on a full buffer.
     */
    private class StreamPump(private val stream: java.io.InputStream) : Thread() {
        private val sink = StringBuilder()

        init {
            isDaemon = true; name = "py-judge-stderr"
        }

        override fun run() {
            try {
                stream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { line -> synchronized(sink) { sink.append(line).append('\n') } }
                }
            } catch (_: Exception) {
                // ignore - drained on a best-effort basis
            }
        }

        fun captured(): String = synchronized(sink) { sink.toString() }
    }

    private class TimeoutCancellation : RuntimeException()
    private class ProcessFailure(val exitCode: Int, val stderrPreview: String) : RuntimeException()

    companion object {
        private val LOG = LoggerFactory.getLogger(PythonVerdictExtension::class.java)
        private const val STDERR_PREVIEW_LEN = 500
    }
}
