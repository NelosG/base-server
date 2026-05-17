package com.nelos.parallel.pipeline.kts

import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.pipeline.commons.enums.ScriptType
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.commons.service.EvaluationContext
import com.nelos.parallel.pipeline.commons.service.EvaluatorScript
import com.nelos.parallel.pipeline.commons.service.SubmissionVerdict
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.script.ScriptEngineManager
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Real-engine smoke tests for [KtsVerdictExtension]. We deliberately keep
 * the script sources tiny: the JSR-223 Kotlin compiler is slow on first
 * use, and longer scripts compound that cost without buying us extra
 * coverage of the wiring under test.
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
    fun ensureKtsAvailable() {
        // Avoid failing on environments where kotlin-scripting-jsr223 was
        // somehow excluded - the production code falls back to baseline in
        // that case, but the tests below assert the success path.
        val engine = ScriptEngineManager().getEngineByExtension("kts")
        assumeTrue(engine != null, "Kotlin JSR-223 engine unavailable; skipping real-script tests")
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

    // --- real engine - only the "script blew up" branch is practical here --
    //
    // The wiring path that calls into `judge { pass() }` / `fail()` works in
    // production but requires the embedded Kotlin compiler to resolve our
    // `JudgeApi` helpers - that takes several seconds per invocation and is
    // sensitive to test-classpath classloader nuances that don't matter to
    // the orchestrator at runtime. Those branches are exercised end-to-end
    // by manual instructor scripts; here we focus on the fast unit-level
    // wiring + the fallback paths.

    @Test
    fun `script that fails to compile falls back to baseline with reason`() {
        val src = "this is not valid kotlin at all !!!"
        val kts = EvaluatorScript(type = ScriptType.KTS, source = src, timeoutMs = 60_000)

        val result = extension.apply(ctx(kts), baseline)

        // Baseline status preserved; reason explains the fallback.
        assertEquals(SubmissionStatus.COMPLETED, result.submissionStatus)
        assertEquals(JobStatus.SUCCESS, result.jobStatus)
        assertNotNull(result.reason)
        // We don't assert the exact wording because Kotlin's diagnostic text
        // changes between versions - but the reason MUST end with our
        // "used baseline" tail so the UI can tell this was a fallback.
        assertContains(result.reason ?: "", "used baseline")
    }
}
