package com.nelos.parallel.pipeline.core.service.impl

import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.commons.extension.ScriptVerdictExtension
import com.nelos.parallel.pipeline.commons.extension.VerdictExtension
import com.nelos.parallel.pipeline.commons.service.EvaluationContext
import com.nelos.parallel.pipeline.commons.service.SubmissionResultEvaluator
import com.nelos.parallel.pipeline.commons.service.SubmissionVerdict
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Default evaluator. Computes a baseline verdict from engine status + test
 * counts, then folds every registered [VerdictExtension] over it so custom
 * instructor scripts (.kts / .py) can override the decision.
 *
 * If an assignment's script type has no matching [ScriptVerdictExtension]
 * registered (e.g. instructor selected Python but `pipeline/python` isn't
 * on the classpath), the evaluator logs a warning, leaves the verdict at
 * baseline, and writes a `reason` so the UI / CI can surface the warning.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.submissionResultEvaluator")
class SubmissionResultEvaluatorImpl(
    private val extensions: List<VerdictExtension>,
) : SubmissionResultEvaluator {

    private val supportedScriptTypes = extensions
        .filterIsInstance<ScriptVerdictExtension>()
        .map { it.scriptType }
        .toSet()

    override fun evaluate(context: EvaluationContext): SubmissionVerdict {
        val baseline = baseline(context.result)
        val script = context.script
        // Disabled-but-present script: instructor parked the code without
        // deleting it. Honour the toggle and skip the extension chain so
        // the default verdict stands silently.
        if (script != null && !script.enabled) {
            return extensions
                .filterNot { it is ScriptVerdictExtension }
                .fold(baseline) { current, ext -> ext.apply(context, current) }
        }
        if (script != null && script.type !in supportedScriptTypes) {
            LOG.warn(
                "Assignment has {} script configured but no runtime is registered - falling back to default verdict",
                script.type,
            )
            return baseline.copy(
                reason = "${script.type} script configured but runtime not loaded - used default test-count verdict",
            )
        }
        return extensions.fold(baseline) { current, ext -> ext.apply(context, current) }
    }

    /** Status-and-summary derived from the engine's TaskResult alone. */
    private fun baseline(result: TaskResult): SubmissionVerdict {
        val testsFailed = hasFailedTests(result)
        return SubmissionVerdict(
            submissionStatus = submissionStatusOf(result, testsFailed),
            jobStatus = jobStatusOf(result, testsFailed),
            summary = summarize(result),
        )
    }

    private fun submissionStatusOf(result: TaskResult, testsFailed: Boolean): SubmissionStatus =
        when (result.status) {
            "completed" -> if (testsFailed) SubmissionStatus.FAILED else SubmissionStatus.COMPLETED
            "failed" -> SubmissionStatus.FAILED
            "cancelled" -> SubmissionStatus.REJECTED
            else -> SubmissionStatus.ERROR
        }

    private fun jobStatusOf(result: TaskResult, testsFailed: Boolean): JobStatus =
        when (result.status) {
            "completed" -> if (testsFailed) JobStatus.FAILED else JobStatus.SUCCESS
            "failed" -> JobStatus.FAILED
            "cancelled" -> JobStatus.INTERRUPTED
            else -> JobStatus.ERROR
        }

    private fun hasFailedTests(result: TaskResult): Boolean {
        val s = result.summary ?: return false
        val corrFailed = s.correctness?.failed ?: 0
        val perfFailed = s.performance?.failed ?: 0
        return corrFailed + perfFailed > 0
    }

    private fun summarize(result: TaskResult): String = buildString {
        append("Status: ${result.status}")
        result.durationMs?.let { append(", Duration: ${it}ms") }
        result.error?.let { append(", Error: $it") }
        result.summary?.let { s ->
            s.correctness?.let { c ->
                val passed = c.passed ?: 0
                val total = c.totalTests ?: ((c.passed ?: 0) + (c.failed ?: 0))
                if (total > 0) append(", Correctness: $passed/$total")
            }
            s.performance?.let { p ->
                val passed = p.passed ?: 0
                val total = p.totalTests ?: ((p.passed ?: 0) + (p.failed ?: 0))
                if (total > 0) append(", Performance: $passed/$total")
            }
        }
    }.take(MAX_SUMMARY_LEN)

    companion object {
        private val LOG = LoggerFactory.getLogger(SubmissionResultEvaluatorImpl::class.java)
        private const val MAX_SUMMARY_LEN = 4000
    }
}
