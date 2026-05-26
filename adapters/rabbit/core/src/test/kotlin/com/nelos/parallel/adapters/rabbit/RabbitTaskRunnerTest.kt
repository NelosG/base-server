package com.nelos.parallel.adapters.rabbit

import com.nelos.parallel.commons.adapter.NodeAdapter
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
class RabbitTaskRunnerTest {

    private val adapter: NodeAdapter = mock()
    private val selector: RunnerSelector = mock()
    private val runner = RabbitTaskRunner(adapter, selector)

    private fun ctx(jobId: String? = "j1") = RunnerContext(
        submissionId = 7L,
        task = TaskSubmission(jobId = jobId, testId = "lab1"),
        onResult = { },
    )

    private fun node(id: String = "engine-A") = NodeInfo(nodeId = id, transports = emptyList())

    @Test
    fun `tryRun without a jobId is an infra failure`() {
        assertThrows<RunnerInfraException> { runner.tryRun(ctx(jobId = null)) }
        verify(selector, never()).selectRunner(any(), any())
    }

    @Test
    fun `no AMQP runner available returns null so the manager can fall over`() {
        whenever(selector.selectRunner(7L, TransportType.AMQP)).thenReturn(null)

        assertNull(runner.tryRun(ctx()))
        verify(adapter, never()).submitTask(any(), any())
    }

    @Test
    fun `happy path submits the task via AMQP and returns a runnable handle`() {
        val n = node()
        whenever(selector.selectRunner(7L, TransportType.AMQP))
            .thenReturn(SelectedRunner(node = n, adapter = adapter, transport = TransportType.AMQP))
        whenever(adapter.submitTask(eq(n), any()))
            .thenReturn(TaskSubmissionResponse(jobId = "j1", status = "accepted"))

        val handle = runner.tryRun(ctx())

        assertNotNull(handle)
        assertEquals("j1", handle.jobId)
        assertEquals("rabbit", handle.runnerName)
    }

    @Test
    fun `submitTask RPC failure still returns a handle - mirrors HTTP runner behavior`() {
        val n = node()
        whenever(selector.selectRunner(7L, TransportType.AMQP))
            .thenReturn(SelectedRunner(node = n, adapter = adapter, transport = TransportType.AMQP))
        whenever(adapter.submitTask(eq(n), any())).doThrow(RuntimeException("ack timeout"))

        val handle = runner.tryRun(ctx())

        // Broker ack does not equal engine acceptance. We leave the submission
        // in-flight and let stuck-cleanup resolve a genuinely lost message.
        assertNotNull(handle)
        assertEquals("j1", handle.jobId)
    }

    @Test
    fun `cancel delegates to adapter cancelJob`() {
        val n = node()
        whenever(selector.selectRunner(7L, TransportType.AMQP))
            .thenReturn(SelectedRunner(node = n, adapter = adapter, transport = TransportType.AMQP))
        whenever(adapter.submitTask(eq(n), any()))
            .thenReturn(TaskSubmissionResponse(jobId = "j1", status = "accepted"))
        val handle = runner.tryRun(ctx())!!

        handle.cancel()

        verify(adapter).cancelJob(eq(n), eq("j1"))
    }

    @Test
    fun `cancel swallows adapter exceptions - cancel is best-effort`() {
        val n = node()
        whenever(selector.selectRunner(7L, TransportType.AMQP))
            .thenReturn(SelectedRunner(node = n, adapter = adapter, transport = TransportType.AMQP))
        whenever(adapter.submitTask(eq(n), any()))
            .thenReturn(TaskSubmissionResponse(jobId = "j1", status = "accepted"))
        whenever(adapter.cancelJob(eq(n), eq("j1"))).doThrow(RuntimeException("broker gone"))
        val handle = runner.tryRun(ctx())!!

        // Must not throw - the caller has already moved on by the time cancel runs.
        handle.cancel()
    }
}
