package com.nelos.parallel.pipeline.kts

import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.pipeline.commons.enums.ScriptType
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.commons.extension.JudgeContext
import com.nelos.parallel.pipeline.commons.extension.JudgeResult
import com.nelos.parallel.pipeline.commons.extension.ScriptVerdictExtension
import com.nelos.parallel.pipeline.commons.service.EvaluationContext
import com.nelos.parallel.pipeline.commons.service.SubmissionVerdict
import com.nelos.parallel.pipeline.kts.api.VerdictScript
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

/**
 * Runs instructor-authored .kts verdict scripts via Kotlin's native
 * scripting-jvm-host (the proper API, not the legacy JSR-223 wrapper). Each
 * script source is compiled once and cached by SHA-256 of its bytes so
 * repeated submissions of the same assignment skip the multi-second
 * compilation cost.
 *
 * Scripts see [JudgeContext] as the `ctx` provided property (declared in
 * [com.nelos.parallel.pipeline.kts.api.VerdictScript]); identity-related
 * data is deliberately kept out of the script's view.
 *
 * Every failure mode is soft - exceptions, timeouts and compilation
 * diagnostics all fall back to the baseline verdict so a broken instructor
 * script never blocks the orchestrator from recording a result. The
 * fallback `reason` surfaces in the UI and CI output so instructors can see
 * what went wrong.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.ktsVerdictExtension")
class KtsVerdictExtension : ScriptVerdictExtension {

    override val scriptType: ScriptType = ScriptType.KTS

    private val host = BasicJvmScriptingHost()
    private val compileConfig by lazy {
        createJvmCompilationConfigurationFromTemplate<VerdictScript>()
    }
    // Short-TTL cache so repeated submissions on the same script skip the
    // multi-second compile cost, while instructor edits propagate within a
    // minute without a JVM restart. Capped to a handful of entries so we
    // can't hold compiled IR for every historical revision.
    private val compiledCache = ConcurrentHashMap<String, CachedScript>()

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
                compile("null")
                LOG.info("KTS verdict-script engine warmed up (scripting-jvm-host)")
            }.onFailure { LOG.warn("KTS engine warm-up failed: {}", it.message) }
        }, "kts-warmup").apply { isDaemon = true }.start()
    }

    override fun apply(context: EvaluationContext, current: SubmissionVerdict): SubmissionVerdict {
        val script = context.script ?: return current
        if (script.type != scriptType) return current

        val judgeContext = JudgeContext(context.result, current)
        val verdict = try {
            runWithTimeout(script.timeoutMs) { evaluate(script.source, judgeContext) }
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

    private fun evaluate(source: String, ctx: JudgeContext): JudgeResult? {
        val compiled = compile(source)
        val evalConfig = ScriptEvaluationConfiguration {
            providedProperties("ctx" to ctx)
        }
        val evalResult: EvaluationResult = runBlocking { host.evaluator(compiled, evalConfig) }.valueOrThrow()
        return when (val rv = evalResult.returnValue) {
            is ResultValue.Value -> rv.value as? JudgeResult
            is ResultValue.Error -> throw rv.error
            else -> null
        }
    }

    private fun compile(source: String): CompiledScript {
        val key = digest(source)
        val now = System.nanoTime()
        compiledCache[key]?.takeIf { now - it.compiledAtNs < CACHE_TTL_NS }?.let { return it.script }
        val scriptSource = source.toScriptSource(name = "verdict.verdict.kts")
        val compiled = runBlocking { host.compiler(scriptSource, compileConfig) }.valueOrThrow()
        compiledCache[key] = CachedScript(compiled, now)
        evictExpired(now)
        return compiled
    }

    private fun evictExpired(now: Long) {
        // Sweep on write only - cheap and good enough for a few-entry cache.
        compiledCache.entries.removeIf { now - it.value.compiledAtNs >= CACHE_TTL_NS }
    }

    private data class CachedScript(val script: CompiledScript, val compiledAtNs: Long)

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

    private fun digest(source: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(source.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) { for (b in bytes) append("%02x".format(b)) }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(KtsVerdictExtension::class.java)
        private val CACHE_TTL_NS = TimeUnit.MINUTES.toNanos(1)
    }
}

private fun <T> ResultWithDiagnostics<T>.valueOrThrow(): T = when (this) {
    is ResultWithDiagnostics.Success -> value
    is ResultWithDiagnostics.Failure -> {
        val errors = reports.filter { it.severity == ScriptDiagnostic.Severity.ERROR || it.severity == ScriptDiagnostic.Severity.FATAL }
        val msg = (if (errors.isNotEmpty()) errors else reports).joinToString("; ") { it.message }
        throw RuntimeException("script compile/eval failed: $msg")
    }
}
