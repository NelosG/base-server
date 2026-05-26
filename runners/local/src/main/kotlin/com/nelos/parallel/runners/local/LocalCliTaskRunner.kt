package com.nelos.parallel.runners.local

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.git.service.GitReacher
import com.nelos.parallel.pipeline.runner.exception.RunnerInfraException
import com.nelos.parallel.pipeline.runner.service.RunHandle
import com.nelos.parallel.pipeline.runner.service.RunnerContext
import com.nelos.parallel.pipeline.runner.service.RunnerSettingsProvider
import com.nelos.parallel.pipeline.runner.service.RunnerType
import com.nelos.parallel.pipeline.runner.service.TaskRunner
import com.nelos.parallel.runners.commons.CliWorkspace
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Spawns the `cli` binary one shot per task. Workspace per job; sources are
 * pre-cloned via [CliWorkspace] so cli only sees local paths.
 *
 * All knobs (binary path, workdir, concurrency cap) live in
 * `prl_runner_config.settings` and are edited via the /runners admin page;
 * this class reads them fresh through [RunnerSettingsProvider] on every
 * dispatch, falling back to [LocalRunnerSettings] defaults when the row is
 * absent.
 *
 * Result delivery: synchronous. This runner is responsible for calling
 * [RunnerContext.onResult] exactly once from the background pool, after the
 * process exits and `result.json` is parsed (or a synthetic ERROR result is
 * built when the process crashed before writing anything readable).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.localRunner")
class LocalCliTaskRunner(
    private val settingsProvider: RunnerSettingsProvider,
    private val gitReacher: GitReacher,
    private val objectMapper: ObjectMapper,
) : TaskRunner {

    override val type: RunnerType = RunnerType.LOCAL
    override val name: String = "local"

    // The background pool sizes itself off the FIRST `maxConcurrent` we see.
    // Admin-side changes to the concurrency cap require a server restart;
    // the alternative (recreating the pool on the fly) would either drop
    // in-flight work or open a thread-safety surface area we don't need.
    @Volatile
    private var executor: ExecutorService? = null

    private val running = ConcurrentHashMap<String, Process>()

    private fun settings(): LocalRunnerSettings =
        settingsProvider.get(name, LocalRunnerSettings::class.java) { LocalRunnerSettings() }

    private fun executor(): ExecutorService {
        // Double-checked locking around the lazily-created pool. We can't use
        // `lazy` because the desired pool size lives in settings, which we
        // only know after Spring is fully up.
        return executor ?: synchronized(this) {
            executor ?: Executors.newFixedThreadPool(settings().maxConcurrent.coerceAtLeast(1)) { r ->
                Thread(r, "prl-local-runner").apply { isDaemon = true }
            }.also { executor = it }
        }
    }

    override fun isAvailable(): Boolean = settings().binaryPath.isNotBlank()

    override fun tryRun(ctx: RunnerContext): RunHandle? {
        val jobId = ctx.task.jobId
            ?: throw RunnerInfraException(name, "task has no jobId")
        val s = settings()
        if (s.binaryPath.isBlank()) return null
        val binary = Path.of(s.binaryPath)
        if (!Files.isRegularFile(binary) || !Files.isExecutable(binary)) {
            throw RunnerInfraException(name, "cli binary not found or not executable: $binary")
        }
        val workspace = CliWorkspace(Path.of(s.workdirBase, jobId), gitReacher)
        try {
            workspace.prepare(ctx.task)
        } catch (e: Exception) {
            workspace.cleanup()
            throw RunnerInfraException(name, "workspace prepare failed: ${e.message}", e)
        }
        val command = buildCommand(binary, ctx, workspace)
        LOG.info("submission {}: spawning local cli: {}", ctx.submissionId, command.joinToString(" "))

        executor().submit { runJob(ctx, jobId, command, workspace, s.shutdownGraceSec) }
        return LocalRunHandle(jobId, running, s.shutdownGraceSec)
    }

    /** Background-thread body: spawn, wait, parse, deliver. */
    private fun runJob(
        ctx: RunnerContext,
        jobId: String,
        command: List<String>,
        workspace: CliWorkspace,
        @Suppress("unused") graceSec: Int,
    ) {
        val stdoutFile = workspace.testsDir.parent.resolve("stdout.log")
        val stderrFile = workspace.testsDir.parent.resolve("stderr.log")
        val startedAt = Instant.now()
        val process = try {
            ProcessBuilder(command)
                .directory(workspace.testsDir.parent.toFile())
                .redirectOutput(stdoutFile.toFile())
                .redirectError(stderrFile.toFile())
                .start()
        } catch (e: Exception) {
            LOG.error("submission {}: cli spawn failed", ctx.submissionId, e)
            ctx.onResult(syntheticError(jobId, "cli spawn failed: ${e.message}"))
            workspace.cleanup()
            return
        }
        running[jobId] = process
        try {
            val exitCode = process.waitFor()
            val durationMs = Duration.between(startedAt, Instant.now()).toMillis().toDouble()
            val result = parseResultOrSynthesize(jobId, workspace.resultFile, exitCode, stderrFile, durationMs)
            ctx.onResult(result)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            process.destroyForcibly()
            ctx.onResult(syntheticError(jobId, "local cli interrupted"))
        } finally {
            running.remove(jobId)
            workspace.cleanup()
        }
    }

    private fun buildCommand(binary: Path, ctx: RunnerContext, workspace: CliWorkspace): List<String> {
        val cmd = mutableListOf<String>(binary.toString())
        ctx.task.testId?.let { cmd += listOf("--test-id", it) }
        cmd += listOf("--test-dir", workspace.testsDir.toString())
        cmd += listOf("--solution", workspace.solutionDir.toString())
        ctx.task.threads?.let { cmd += listOf("--threads", it.toString()) }
        ctx.task.memoryLimitMb?.let { cmd += listOf("--memory-limit", it.toString()) }
        ctx.task.wallTimeSec?.let { cmd += listOf("--wall-time", it.toString()) }
        ctx.task.cpuTimeSec?.let { cmd += listOf("--cpu-time", it.toString()) }
        ctx.task.warmupIterations?.let { cmd += listOf("--warmup", it.toString()) }
        cmd += listOf("--output", workspace.resultFile.toString())
        return cmd
    }

    internal fun parseResultOrSynthesize(
        jobId: String,
        resultFile: Path,
        exitCode: Int,
        stderrFile: Path,
        durationMs: Double,
    ): TaskResult {
        if (Files.isRegularFile(resultFile)) {
            // cli emits either a single object (one solution) or a JSON array.
            // Pipeline submits one solution per submission, so unwrap the
            // array if present and pick the first element.
            val rootNode = try {
                objectMapper.readTree(resultFile.toFile())
            } catch (e: Exception) {
                LOG.error("submission jobId={}: result.json malformed", jobId, e)
                return syntheticError(jobId, "result.json malformed: ${e.message}", durationMs)
            }
            val taskNode = if (rootNode.isArray) rootNode.firstOrNull() ?: return syntheticError(
                jobId, "result.json was an empty array", durationMs
            ) else rootNode
            val parsed = try {
                objectMapper.treeToValue(taskNode, TaskResult::class.java)
            } catch (e: Exception) {
                LOG.error("submission jobId={}: TaskResult deserialization failed", jobId, e)
                return syntheticError(jobId, "TaskResult deserialization failed: ${e.message}", durationMs)
            }
            return if (parsed.jobId.isBlank()) parsed.withJobId(jobId) else parsed
        }
        val errTail = try {
            if (Files.isRegularFile(stderrFile)) Files.readString(stderrFile).takeLast(2000) else ""
        } catch (_: Exception) {
            ""
        }
        val reason = if (exitCode != 0) "cli exited with code $exitCode" else "cli exited 0 but wrote no result.json"
        return syntheticError(jobId, "$reason${if (errTail.isNotEmpty()) " - $errTail" else ""}", durationMs)
    }

    private fun syntheticError(jobId: String, message: String, durationMs: Double? = null): TaskResult =
        TaskResult(
            jobId = jobId,
            nodeId = "local",
            status = "error",
            error = message,
            durationMs = durationMs,
        )

    @PreDestroy
    fun shutdown() {
        val grace = settings().shutdownGraceSec.toLong()
        running.values.forEach { p ->
            if (p.isAlive) {
                p.destroy()
                if (!p.waitFor(grace, TimeUnit.SECONDS)) p.destroyForcibly()
            }
        }
        executor?.shutdownNow()
    }

    private class LocalRunHandle(
        override val jobId: String,
        private val running: ConcurrentHashMap<String, Process>,
        private val graceSec: Int,
    ) : RunHandle {
        override val runnerName: String = "local"
        override fun cancel() {
            val p = running[jobId] ?: return
            if (!p.isAlive) return
            p.destroy()
            if (!p.waitFor(graceSec.toLong(), TimeUnit.SECONDS)) p.destroyForcibly()
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(LocalCliTaskRunner::class.java)
        private fun TaskResult.withJobId(newJobId: String): TaskResult = TaskResult(
            jobId = newJobId, nodeId = this.nodeId, status = this.status, error = this.error,
            durationMs = this.durationMs, timestamp = this.timestamp, mode = this.mode,
            solution = this.solution, buildOutput = this.buildOutput, correctness = this.correctness,
            performance = this.performance, buildInfo = this.buildInfo, threadCounts = this.threadCounts,
            totalTimeMs = this.totalTimeMs, assignmentConfig = this.assignmentConfig, pipeline = this.pipeline,
            effectiveParams = this.effectiveParams, environment = this.environment, failedStep = this.failedStep,
            errorDetails = this.errorDetails, testsDiscovered = this.testsDiscovered, summary = this.summary,
            performanceSkipped = this.performanceSkipped, performanceSkipReason = this.performanceSkipReason,
        )
    }
}
