package com.nelos.parallel.adapters.http

import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.request.TaskSubmission
import com.nelos.parallel.commons.adapter.vo.response.TaskSubmissionResponse
import com.nelos.parallel.pipeline.commons.service.RunnerSelector
import com.nelos.parallel.pipeline.commons.service.SelectedRunner
import com.nelos.parallel.pipeline.runner.exception.RunnerInfraException
import com.nelos.parallel.pipeline.runner.service.RunnerContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class HttpTaskRunnerTest {

    private val adapter: HttpNodeAdapter = mock()
    private val selector: RunnerSelector = mock()
    private val runner = HttpTaskRunner(adapter, selector)

    private fun ctx(jobId: String? = "job-1", onResult: (Any) -> Unit = {}): RunnerContext =
        RunnerContext(
            submissionId = 42L,
            task = TaskSubmission(jobId = jobId, testId = "lab1"),
            onResult = { onResult(it) },
        )

    private fun node(id: String = "engine-A") = NodeInfo(nodeId = id, transports = emptyList())

    @Test
    fun `task without a jobId is an infra failure`() {
        assertThrows<RunnerInfraException> { runner.tryRun(ctx(jobId = null)) }
        verify(selector, never()).selectRunner(any(), any())
    }

    @Test
    fun `no HTTP runner available - returns null so the manager can fall over`() {
        whenever(selector.selectRunner(42L, TransportType.HTTP)).thenReturn(null)

        val handle = runner.tryRun(ctx())

        assertNull(handle)
        verify(adapter, never()).submitTask(any(), any())
    }

    @Test
    fun `happy path submits the task and returns a runnable handle`() {
        val n = node()
        whenever(selector.selectRunner(42L, TransportType.HTTP))
            .thenReturn(SelectedRunner(node = n, adapter = adapter, transport = TransportType.HTTP))
        whenever(adapter.submitTask(eq(n), any()))
            .thenReturn(TaskSubmissionResponse(jobId = "job-1", status = "accepted"))

        val handle = runner.tryRun(ctx())

        assertNotNull(handle)
        assertEquals("job-1", handle.jobId)
        assertEquals("http", handle.runnerName)
        verify(adapter).submitTask(eq(n), any())
    }

    @Test
    fun `submitTask RPC failure still returns a handle - the engine may have received the task`() {
        val n = node()
        whenever(selector.selectRunner(42L, TransportType.HTTP))
            .thenReturn(SelectedRunner(node = n, adapter = adapter, transport = TransportType.HTTP))
        whenever(adapter.submitTask(eq(n), any())).doThrow(RuntimeException("timeout"))

        val handle = runner.tryRun(ctx())

        // Leaving the in-flight submission in place is intentional - stuck cleanup
        // will resolve the case where the engine genuinely never received it.
        assertNotNull(handle)
        assertEquals("job-1", handle.jobId)
    }

    @Test
    fun `cancel delegates to adapter cancelJob`() {
        val n = node()
        whenever(selector.selectRunner(42L, TransportType.HTTP))
            .thenReturn(SelectedRunner(node = n, adapter = adapter, transport = TransportType.HTTP))
        whenever(adapter.submitTask(eq(n), any()))
            .thenReturn(TaskSubmissionResponse(jobId = "job-1", status = "accepted"))
        val handle = runner.tryRun(ctx())!!

        handle.cancel()

        verify(adapter).cancelJob(eq(n), eq("job-1"))
    }

    @Test
    fun `cancel swallows adapter exceptions - cancel is best-effort`() {
        val n = node()
        whenever(selector.selectRunner(42L, TransportType.HTTP))
            .thenReturn(SelectedRunner(node = n, adapter = adapter, transport = TransportType.HTTP))
        whenever(adapter.submitTask(eq(n), any()))
            .thenReturn(TaskSubmissionResponse(jobId = "job-1", status = "accepted"))
        whenever(adapter.cancelJob(eq(n), eq("job-1"))).doThrow(RuntimeException("node gone"))
        val handle = runner.tryRun(ctx())!!

        // Must not throw - the caller has already moved on by the time cancel runs.
        handle.cancel()
    }
}
