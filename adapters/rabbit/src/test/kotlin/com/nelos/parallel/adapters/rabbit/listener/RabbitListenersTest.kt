package com.nelos.parallel.adapters.rabbit.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.adapter.listener.TaskResultListenerRegistry
import com.nelos.parallel.commons.adapter.vo.request.ProgressEvent
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.pipeline.commons.service.PipelineService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import kotlin.test.assertEquals

/**
 * Acknowledgement semantics are critical: malformed payloads must be swallowed
 * (poison-pill protection - otherwise the queue loops forever), while
 * processing failures must propagate so the broker requeues. Both listeners
 * follow the same pattern; we test them together.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class RabbitListenersTest {

    private val objectMapper = ObjectMapper()
    private val pipelineService: PipelineService = mock()

    @Nested
    inner class Results {

        private val registry: TaskResultListenerRegistry = mock()
        private val listener = RabbitResultListener(objectMapper, pipelineService, registry)

        @Test
        fun `malformed JSON is acked silently and never reaches the pipeline`() {
            listener.onMessage("not-json".toByteArray())

            verify(pipelineService, never()).handleResult(any())
            verify(registry, never()).dispatch(any())
        }

        @Test
        fun `well-formed payload is forwarded to pipeline AND to the listener registry`() {
            val body = objectMapper.writeValueAsBytes(TaskResult(jobId = "1", status = "completed"))

            listener.onMessage(body)

            val captor = argumentCaptor<TaskResult>()
            verify(pipelineService).handleResult(captor.capture())
            assertEquals("1", captor.firstValue.jobId)
            verify(registry).dispatch(any())
        }

        @Test
        fun `registry dispatch error does NOT mask a successful pipeline write`() {
            val body = objectMapper.writeValueAsBytes(TaskResult(jobId = "1", status = "completed"))
            whenever(registry.dispatch(any())).doThrow(RuntimeException("adapter-test bug"))

            // Must not throw: the adapter-test UI hook is best-effort. Throwing here
            // would AMQP-NACK and requeue the message, causing endless re-delivery.
            listener.onMessage(body)

            verify(pipelineService).handleResult(any())
        }

        @Test
        fun `pipeline failure DOES propagate so the broker can requeue`() {
            val body = objectMapper.writeValueAsBytes(TaskResult(jobId = "1", status = "completed"))
            whenever(pipelineService.handleResult(any())).doThrow(RuntimeException("DB down"))

            val e = assertThrows<RuntimeException> { listener.onMessage(body) }
            assertEquals("DB down", e.message)
        }
    }

    @Nested
    inner class Progress {

        private val listener = RabbitProgressListener(objectMapper, pipelineService)

        @Test
        fun `malformed JSON is dropped without reaching the pipeline`() {
            listener.onMessage("garbage".toByteArray())

            verify(pipelineService, never()).handleProgress(any())
        }

        @Test
        fun `well-formed event is forwarded as-is`() {
            val body = objectMapper.writeValueAsBytes(
                ProgressEvent(jobId = "7", phase = "resolveTests", message = "cloning"),
            )

            listener.onMessage(body)

            val captor = argumentCaptor<ProgressEvent>()
            verify(pipelineService).handleProgress(captor.capture())
            assertEquals("7", captor.firstValue.jobId)
            assertEquals("resolveTests", captor.firstValue.phase)
        }

        @Test
        fun `pipeline failure propagates so the broker can requeue`() {
            val body = objectMapper.writeValueAsBytes(ProgressEvent(jobId = "7", phase = "test"))
            whenever(pipelineService.handleProgress(any())).doThrow(RuntimeException("DB down"))

            val e = assertThrows<RuntimeException> { listener.onMessage(body) }
            assertEquals("DB down", e.message)
        }
    }
}
