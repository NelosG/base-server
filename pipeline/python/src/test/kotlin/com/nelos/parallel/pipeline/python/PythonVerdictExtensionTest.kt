package com.nelos.parallel.pipeline.python

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.pipeline.commons.enums.ScriptType
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.commons.service.EvaluationContext
import com.nelos.parallel.pipeline.commons.service.EvaluatorScript
import com.nelos.parallel.pipeline.commons.service.SubmissionVerdict
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Unit-level tests for [PythonVerdictExtension] cover everything that runs
 * before - and the failure paths around - the actual `python` subprocess.
 * Spawning real Python would be an integration test (and is OS-dependent),
 * so we intentionally don't exercise the happy path here.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class PythonVerdictExtensionTest {

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

    @Test
    fun `no script attached returns the baseline verdict untouched`() {
        val ext = PythonVerdictExtension(ObjectMapper())

        val result = ext.apply(ctx(script = null), baseline)

        assertSame(baseline, result)
    }

    @Test
    fun `script of a foreign type returns the baseline verdict untouched`() {
        val ext = PythonVerdictExtension(ObjectMapper())
        val kts = EvaluatorScript(type = ScriptType.KTS, source = "ignored")

        val result = ext.apply(ctx(kts), baseline)

        assertSame(baseline, result)
    }

    @Test
    fun `JudgeContext serialization failure falls back to baseline with reason`() {
        val mapper: ObjectMapper = mock()
        whenever(mapper.writeValueAsString(any())).doThrow(object : JsonProcessingException("bad") {})
        val ext = PythonVerdictExtension(mapper)

        val pyScript = EvaluatorScript(type = ScriptType.PYTHON, source = "print(0)")
        val result = ext.apply(ctx(pyScript), baseline)

        assertEquals(SubmissionStatus.COMPLETED, result.submissionStatus)
        assertContains(result.reason ?: "", "serialise JudgeContext")
    }
}
