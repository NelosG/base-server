package com.nelos.parallel.pipeline.kts

import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.pipeline.commons.enums.ScriptType
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.commons.extension.JudgeContext
import com.nelos.parallel.pipeline.commons.extension.JudgeResult
import com.nelos.parallel.pipeline.commons.extension.ScriptVerdictExtension
import com.nelos.parallel.pipeline.commons.service.EvaluationContext
import com.nelos.parallel.pipeline.commons.service.SubmissionVerdict
import com.nelos.parallel.pipeline.kts.api.currentJudgeContext
import com.nelos.parallel.pipeline.kts.api.currentVerdict
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

/**
 * Runs instructor-authored .kts verdict scripts via Kotlin's JSR-223
 * engine. The script sees the [JudgeContext] through the `judge { }` DSL
 * helper in `JudgeApi.kt`; identity-related data is deliberately kept out
 * of the script's view.
 *
 * Every failure mode is soft - exceptions, timeouts, and a missing engine
 * all fall back to the baseline verdict so a broken instructor script
 * never blocks the orchestrator from recording a result. The fallback
 * `reason` surfaces in the UI and CI output so instructors can see what
 * went wrong.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.ktsVerdictExtension")
class KtsVerdictExtension : ScriptVerdictExtension {

    override val scriptType: ScriptType = ScriptType.KTS

    private val engine: ScriptEngine? by lazy {
        try {
            val e = ScriptEngineManager().getEngineByExtension("kts")
            if (e == null) {
                LOG.warn("Kotlin JSR-223 script engine not available on classpath (kts)")
            } else {
                LOG.info("KTS verdict-script engine initialised: {}", e.factory.engineName)
            }
            e
        } catch (t: Throwable) {
            LOG.warn("Failed to initialise KTS script engine: {}", t.message, t)
            null
        }
    }

    /**
     * Kotlin's embedded compiler pays a several-second cost on its first
     * use - without a warm-up the very first submission would race that
     * cost against its own timeout. Warm-up runs on a daemon thread so
     * application boot is not delayed by it.
     */
    @PostConstruct
    fun warmUp() {
        Thread({
            runCatching {
                engine?.let { runOnce(it, "1") }
                LOG.info("KTS engine warm-up done")
            }.onFailure { LOG.warn("KTS engine warm-up failed: {}", it.message) }
        }, "kts-warmup").apply { isDaemon = true }.start()
    }

    override fun apply(context: EvaluationContext, current: SubmissionVerdict): SubmissionVerdict {
        val script = context.script ?: return current
        if (script.type != scriptType) return current

        val eng = engine ?: return current.copy(
            reason = "KTS engine not available - using baseline verdict",
        )

        val judgeContext = JudgeContext(context.result, current)
        val verdict = try {
            runWithTimeout(script.timeoutMs) {
                currentJudgeContext.set(judgeContext)
                currentVerdict.set(null)
                try {
                    val raw = runOnce(eng, script.source)
                    (raw as? JudgeResult) ?: currentVerdict.get()
                } finally {
                    currentJudgeContext.remove()
                    currentVerdict.remove()
                }
            }
        } catch (e: TimeoutException) {
            LOG.warn("KTS verdict script timed out after {} ms", script.timeoutMs)
            return current.copy(reason = "KTS script timed out after ${script.timeoutMs} ms - used baseline")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return current.copy(reason = "KTS script execution was interrupted - used baseline")
        } catch (e: Exception) {
            LOG.warn("KTS verdict script failed: {}", e.message, e)
            return current.copy(reason = "KTS script error: ${e.message ?: e::class.simpleName} - used baseline")
        }
        verdict ?: return current.copy(
            reason = "KTS script returned no verdict - used baseline",
        )
        return translate(verdict, current)
    }

    /** Thin wrapper around [ScriptEngine.eval] kept as a one-liner. */
    private fun runOnce(engine: ScriptEngine, source: String): Any? = engine.eval(source)

    private fun translate(judge: JudgeResult, baseline: SubmissionVerdict): SubmissionVerdict =
        SubmissionVerdict(
            submissionStatus = if (judge.pass) SubmissionStatus.COMPLETED else SubmissionStatus.FAILED,
            jobStatus = if (judge.pass) JobStatus.SUCCESS else JobStatus.FAILED,
            summary = judge.summary ?: baseline.summary,
            reason = judge.reason,
        )

    /**
     * Run [block] on a daemon thread and abort if it overruns [timeoutMs].
     * Cancellation is cooperative - `Thread.interrupt()` only unblocks
     * scripts that respect interrupts (sleep, blocking I/O, lock waits).
     * A tight CPU loop won't actually stop, but the timeout still
     * surfaces and the caller falls back to baseline.
     */
    private fun <T> runWithTimeout(timeoutMs: Long, block: () -> T): T {
        val executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "kts-judge-script").apply { isDaemon = true }
        }
        try {
            val future = executor.submit<T>(block)
            return try {
                future.get(timeoutMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                future.cancel(true)
                throw e
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        } finally {
            executor.shutdownNow()
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(KtsVerdictExtension::class.java)
    }
}
