package com.nelos.parallel.pipeline.core.service.impl

import com.nelos.parallel.commons.adapter.vo.response.ResultSummary
import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.pipeline.commons.enums.ScriptType
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.commons.extension.ScriptVerdictExtension
import com.nelos.parallel.pipeline.commons.extension.VerdictExtension
import com.nelos.parallel.pipeline.commons.service.EvaluationContext
import com.nelos.parallel.pipeline.commons.service.EvaluatorScript
import com.nelos.parallel.pipeline.commons.service.SubmissionVerdict
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class SubmissionResultEvaluatorImplTest {

    // --- helpers --------------------------------------------------------

    private fun ctx(
        status: String = "completed",
        correctnessPassed: Int? = null,
        correctnessFailed: Int? = null,
        performancePassed: Int? = null,
        performanceFailed: Int? = null,
        durationMs: Double? = null,
        error: String? = null,
        script: EvaluatorScript? = null,
    ): EvaluationContext {
        val correctness = if (correctnessPassed != null || correctnessFailed != null) {
            testSummary(
                totalTests = (correctnessPassed ?: 0) + (correctnessFailed ?: 0),
                passed = correctnessPassed, failed = correctnessFailed,
            )
        } else null
        val performance = if (performancePassed != null || performanceFailed != null) {
            testSummary(
                totalTests = (performancePassed ?: 0) + (performanceFailed ?: 0),
                passed = performancePassed, failed = performanceFailed,
            )
        } else null
        val summary = if (correctness != null || performance != null) {
            ResultSummary(correctness = correctness, performance = performance)
        } else null
        return EvaluationContext(
            result = taskResult(
                status = status,
                durationMs = durationMs, error = error, summary = summary,
            ),
            script = script,
        )
    }

    private fun fakeScriptExt(type: ScriptType, transform: (SubmissionVerdict) -> SubmissionVerdict) =
        object : ScriptVerdictExtension {
            override val scriptType = type
            override fun apply(context: EvaluationContext, current: SubmissionVerdict) = transform(current)
        }

    // --- baseline matrix ------------------------------------------------

    @Nested
    inner class Baseline {

        @Test
        fun `completed with no failed tests yields COMPLETED + SUCCESS`() {
            val evaluator = SubmissionResultEvaluatorImpl(extensions = emptyList())

            val v = evaluator.evaluate(ctx(status = "completed", correctnessPassed = 5))

            assertEquals(SubmissionStatus.COMPLETED, v.submissionStatus)
            assertEquals(JobStatus.SUCCESS, v.jobStatus)
        }

        @Test
        fun `completed with failed correctness flips to FAILED + FAILED`() {
            val evaluator = SubmissionResultEvaluatorImpl(extensions = emptyList())

            val v = evaluator.evaluate(ctx(status = "completed", correctnessPassed = 5, correctnessFailed = 2))

            assertEquals(SubmissionStatus.FAILED, v.submissionStatus)
            assertEquals(JobStatus.FAILED, v.jobStatus)
        }

        @Test
        fun `completed with failed performance flips to FAILED too`() {
            val evaluator = SubmissionResultEvaluatorImpl(extensions = emptyList())

            val v = evaluator.evaluate(ctx(status = "completed", performancePassed = 2, performanceFailed = 1))

            assertEquals(SubmissionStatus.FAILED, v.submissionStatus)
            assertEquals(JobStatus.FAILED, v.jobStatus)
        }

        @Test
        fun `failed engine status produces FAILED + FAILED regardless of test counts`() {
            val evaluator = SubmissionResultEvaluatorImpl(extensions = emptyList())

            val v = evaluator.evaluate(ctx(status = "failed", correctnessPassed = 10))

            assertEquals(SubmissionStatus.FAILED, v.submissionStatus)
            assertEquals(JobStatus.FAILED, v.jobStatus)
        }

        @Test
        fun `cancelled status maps to REJECTED + INTERRUPTED`() {
            val evaluator = SubmissionResultEvaluatorImpl(extensions = emptyList())

            val v = evaluator.evaluate(ctx(status = "cancelled"))

            assertEquals(SubmissionStatus.REJECTED, v.submissionStatus)
            assertEquals(JobStatus.INTERRUPTED, v.jobStatus)
        }

        @Test
        fun `unknown status falls back to ERROR + ERROR`() {
            val evaluator = SubmissionResultEvaluatorImpl(extensions = emptyList())

            val v = evaluator.evaluate(ctx(status = "weird-state"))

            assertEquals(SubmissionStatus.ERROR, v.submissionStatus)
            assertEquals(JobStatus.ERROR, v.jobStatus)
        }
    }

    // --- summary formatting ---------------------------------------------

    @Nested
    inner class SummaryRendering {

        @Test
        fun `summary includes status duration and test counts`() {
            val evaluator = SubmissionResultEvaluatorImpl(extensions = emptyList())

            val v = evaluator.evaluate(
                ctx(
                    status = "completed", durationMs = 150.0,
                    correctnessPassed = 5, performancePassed = 2,
                ),
            )

            assertContains(v.summary, "Status: completed")
            assertContains(v.summary, "Duration: 150.0ms")
            assertContains(v.summary, "Correctness: 5/5")
            assertContains(v.summary, "Performance: 2/2")
        }

        @Test
        fun `summary status reflects the verdict not the raw engine status`() {
            val evaluator = SubmissionResultEvaluatorImpl(extensions = emptyList())

            // Engine: completed; tests: some perf failed -> verdict is FAILED.
            // The leading "Status:" must show "failed", not "completed".
            val v = evaluator.evaluate(
                ctx(status = "completed", performancePassed = 2, performanceFailed = 2),
            )

            assertEquals(SubmissionStatus.FAILED, v.submissionStatus)
            assertContains(v.summary, "Status: failed")
        }

        @Test
        fun `cancelled engine status shows as rejected in summary`() {
            val evaluator = SubmissionResultEvaluatorImpl(extensions = emptyList())

            val v = evaluator.evaluate(ctx(status = "cancelled"))

            assertContains(v.summary, "Status: rejected")
        }

        @Test
        fun `summary includes error when engine reports one`() {
            val evaluator = SubmissionResultEvaluatorImpl(extensions = emptyList())

            val v = evaluator.evaluate(ctx(status = "failed", error = "boom"))

            assertContains(v.summary, "Error: boom")
        }

        @Test
        fun `summary is capped at 4000 chars`() {
            val evaluator = SubmissionResultEvaluatorImpl(extensions = emptyList())
            val huge = "x".repeat(8000)

            val v = evaluator.evaluate(ctx(status = "failed", error = huge))

            assertEquals(4000, v.summary.length)
        }
    }

    // --- extension chain ------------------------------------------------

    @Nested
    inner class Extensions {

        @Test
        fun `extensions are folded in order`() {
            val ext1 = VerdictExtension { _, current -> current.copy(summary = current.summary + "+ext1") }
            val ext2 = VerdictExtension { _, current -> current.copy(summary = current.summary + "+ext2") }
            val evaluator = SubmissionResultEvaluatorImpl(extensions = listOf(ext1, ext2))

            val v = evaluator.evaluate(ctx(status = "completed", correctnessPassed = 1))

            // ext2 ran after ext1
            val pos1 = v.summary.indexOf("+ext1")
            val pos2 = v.summary.indexOf("+ext2")
            assert(pos1 in 0..<pos2) { "expected ext1 before ext2 in '${v.summary}'" }
        }

        @Test
        fun `disabled script skips ScriptVerdictExtensions but runs the rest`() {
            val plain = VerdictExtension { _, current -> current.copy(reason = "plain-touched") }
            val ktsExt = fakeScriptExt(ScriptType.KTS) { it.copy(reason = "kts-touched") }
            val evaluator = SubmissionResultEvaluatorImpl(extensions = listOf(plain, ktsExt))

            val script = EvaluatorScript(type = ScriptType.KTS, source = "println()", enabled = false)
            val v = evaluator.evaluate(ctx(status = "completed", correctnessPassed = 1, script = script))

            assertEquals("plain-touched", v.reason)
        }

        @Test
        fun `missing runtime for configured script type falls back with reason`() {
            // Only KTS is registered; assignment has PYTHON.
            val ktsExt = fakeScriptExt(ScriptType.KTS) { it.copy(reason = "should not run") }
            val evaluator = SubmissionResultEvaluatorImpl(extensions = listOf(ktsExt))

            val script = EvaluatorScript(type = ScriptType.PYTHON, source = "print(0)")
            val v = evaluator.evaluate(ctx(status = "completed", correctnessPassed = 1, script = script))

            assertEquals(SubmissionStatus.COMPLETED, v.submissionStatus)
            assertContains(v.reason ?: "", "PYTHON script configured but runtime not loaded")
        }

        @Test
        fun `script of matching type goes through the full chain`() {
            val ktsExt = fakeScriptExt(ScriptType.KTS) {
                it.copy(submissionStatus = SubmissionStatus.FAILED, reason = "kts said no")
            }
            val evaluator = SubmissionResultEvaluatorImpl(extensions = listOf(ktsExt))

            val script = EvaluatorScript(type = ScriptType.KTS, source = "x")
            val v = evaluator.evaluate(ctx(status = "completed", correctnessPassed = 5, script = script))

            assertEquals(SubmissionStatus.FAILED, v.submissionStatus)
            assertEquals("kts said no", v.reason)
        }

    }
}
