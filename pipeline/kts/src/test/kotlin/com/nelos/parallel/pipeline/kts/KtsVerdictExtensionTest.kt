package com.nelos.parallel.pipeline.kts

import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.pipeline.commons.enums.ScriptType
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.commons.service.EvaluationContext
import com.nelos.parallel.pipeline.commons.service.EvaluatorScript
import com.nelos.parallel.pipeline.commons.service.SubmissionVerdict
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Real-engine smoke tests for [KtsVerdictExtension]. We use a single shared
 * extension instance (PER_CLASS) so the multi-second compiler warm-up is
 * paid once rather than per test. Long-form integration of scripts living
 * in [com.nelos.parallel.pipeline.kts.api.JudgeApi] is now reliable thanks
 * to scripting-jvm-host - the legacy JSR-223 engine could only get to the
 * fallback paths here.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KtsVerdictExtensionTest {

    private val extension = KtsVerdictExtension()

    private val baseline = SubmissionVerdict(
        submissionStatus = SubmissionStatus.COMPLETED,
        jobStatus = JobStatus.SUCCESS,
        summary = "baseline-summary",
    )

    private fun ctx(script: EvaluatorScript?): EvaluationContext =
        EvaluationContext(
            result = TaskResult(jobId = "1", status = "completed"),
            script = script,
        )

    @BeforeAll
    fun warmUpCompiler() {
        // Pay the embedded-compiler boot cost once; later tests use the cache.
        extension.apply(
            ctx(EvaluatorScript(type = ScriptType.KTS, source = "null", timeoutMs = 60_000)),
            baseline,
        )
    }

    // --- passthrough paths (do not touch the engine) --------------------

    @Test
    fun `no script attached returns the baseline verdict untouched`() {
        val result = extension.apply(ctx(script = null), baseline)

        assertSame(baseline, result)
    }

    @Test
    fun `script of a foreign type returns the baseline verdict untouched`() {
        val python = EvaluatorScript(type = ScriptType.PYTHON, source = "ignored")

        val result = extension.apply(ctx(python), baseline)

        assertSame(baseline, result)
    }

    // --- engine paths ---------------------------------------------------

    @Test
    fun `script returning pass yields a COMPLETED verdict`() {
        val src = """pass(summary = "perfect", reason = "ok")"""
        val kts = EvaluatorScript(type = ScriptType.KTS, source = src, timeoutMs = 60_000)

        val result = extension.apply(ctx(kts), baseline)

        assertEquals(SubmissionStatus.COMPLETED, result.submissionStatus)
        assertEquals(JobStatus.SUCCESS, result.jobStatus)
        assertEquals("perfect", result.summary)
        assertEquals("ok", result.reason)
    }

    @Test
    fun `script returning fail yields a FAILED verdict`() {
        val src = """fail(reason = "perf below threshold")"""
        val kts = EvaluatorScript(type = ScriptType.KTS, source = src, timeoutMs = 60_000)

        val result = extension.apply(ctx(kts), baseline)

        assertEquals(SubmissionStatus.FAILED, result.submissionStatus)
        assertEquals(JobStatus.FAILED, result.jobStatus)
        assertEquals("baseline-summary", result.summary)
        assertEquals("perf below threshold", result.reason)
    }

    @Test
    fun `script can read ctx and call followBaseline`() {
        val src = """
            if (ctx.result.status == "completed") ctx.followBaseline("looked ok")
            else fail("status wasn't completed")
        """.trimIndent()
        val kts = EvaluatorScript(type = ScriptType.KTS, source = src, timeoutMs = 60_000)

        val result = extension.apply(ctx(kts), baseline)

        assertEquals(SubmissionStatus.COMPLETED, result.submissionStatus)
        assertEquals("baseline-summary", result.summary)
        assertEquals("looked ok", result.reason)
    }

    @Test
    fun `script returning Unit falls back to baseline with reason`() {
        val src = """val x = 42"""
        val kts = EvaluatorScript(type = ScriptType.KTS, source = src, timeoutMs = 60_000)

        val result = extension.apply(ctx(kts), baseline)

        assertEquals(SubmissionStatus.COMPLETED, result.submissionStatus)
        assertContains(result.reason ?: "", "no verdict")
    }

    @Test
    fun `script that fails to compile falls back to baseline with reason`() {
        val src = "this is not valid kotlin at all !!!"
        val kts = EvaluatorScript(type = ScriptType.KTS, source = src, timeoutMs = 60_000)

        val result = extension.apply(ctx(kts), baseline)

        assertEquals(SubmissionStatus.COMPLETED, result.submissionStatus)
        assertEquals(JobStatus.SUCCESS, result.jobStatus)
        assertNotNull(result.reason)
        assertContains(result.reason ?: "", "used baseline")
    }

    @Test
    fun `script that throws falls back to baseline with reason`() {
        val src = """error("kaboom")"""
        val kts = EvaluatorScript(type = ScriptType.KTS, source = src, timeoutMs = 60_000)

        val result = extension.apply(ctx(kts), baseline)

        assertEquals(SubmissionStatus.COMPLETED, result.submissionStatus)
        assertContains(result.reason ?: "", "kaboom")
        assertContains(result.reason ?: "", "used baseline")
    }
}
