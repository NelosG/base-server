package com.nelos.parallel.adapters.rabbit.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.adapters.rabbit.RabbitConstants
import com.nelos.parallel.adapters.rabbit.exceptions.RabbitAdapterException
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.request.ConfigUpdateRequest
import com.nelos.parallel.commons.adapter.vo.request.TaskSubmission
import com.nelos.parallel.commons.adapter.vo.response.TaskSubmissionResponse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Duration
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Focused unit tests for the bits of [RabbitNodeAdapterImpl] that don't
 * require a live broker - the message-shape contract for `submitTask`,
 * health-check semantics, and the error-wrapping behaviour of `adapterCall`.
 *
 * Methods that are pure "send control RPC -> parse response" pass-throughs
 * (queryNodeStatus, listAdapters, etc.) are exercised only through
 * `updateConfig`, which carries the most contract risk (the engine API
 * expects the payload wrapped in `"config": {...}`).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class RabbitNodeAdapterImplTest {

    private val rabbitTemplate: RabbitTemplate = mock()
    private val controlRabbitTemplate: RabbitTemplate = mock()
    private val objectMapper = ObjectMapper()
    private val stuckTimeout: Duration = Duration.ofMinutes(7)

    private val adapter = RabbitNodeAdapterImpl(
        rabbitTemplate, controlRabbitTemplate, objectMapper, stuckTimeout,
    )

    private fun node(id: String = "n1") = NodeInfo(nodeId = id, transports = emptyList())

    private fun jsonReply(payload: Any): Message =
        Message(objectMapper.writeValueAsBytes(payload), org.springframework.amqp.core.MessageProperties())

    // --- submitTask -----------------------------------------------------

    @Nested
    inner class SubmitTask {

        @Test
        fun `sends the task to the correctness routing key with nodeId header and per-message TTL`() {
            val response = TaskSubmissionResponse(jobId = "1", status = "accepted")
            whenever(rabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
                .thenReturn(jsonReply(response))

            adapter.submitTask(
                node("engine-A"),
                TaskSubmission(jobId = "1", testId = "lab1"),
            )

            val exchange = argumentCaptor<String>()
            val routingKey = argumentCaptor<String>()
            val msg = argumentCaptor<Message>()
            verify(rabbitTemplate).sendAndReceive(exchange.capture(), routingKey.capture(), msg.capture())

            assertEquals(RabbitConstants.TEST_DIRECT_EXCHANGE, exchange.firstValue)
            assertEquals(RabbitConstants.ROUTING_KEY_CORRECTNESS, routingKey.firstValue)
            // Headers + TTL - both pieces matter at runtime.
            assertEquals("engine-A", msg.firstValue.messageProperties.headers["nodeId"])
            assertEquals(stuckTimeout.toMillis().toString(), msg.firstValue.messageProperties.expiration)
            // Sanity: the body deserializes back to a TaskSubmission with the right jobId.
            val sent = objectMapper.readValue(msg.firstValue.body, TaskSubmission::class.java)
            assertEquals("1", sent.jobId)
        }

        @Test
        fun `no reply from broker raises a RabbitAdapterException`() {
            whenever(rabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
                .thenReturn(null)

            val ex = assertThrows<RabbitAdapterException> {
                adapter.submitTask(node("dead"), TaskSubmission(jobId = "1"))
            }
            assertContains(ex.message ?: "", "No response")
        }

        @Test
        fun `does NOT use the short-timeout control template for task submission`() {
            whenever(rabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
                .thenReturn(jsonReply(TaskSubmissionResponse(jobId = "1", status = "accepted")))

            adapter.submitTask(node(), TaskSubmission(jobId = "1"))

            verify(controlRabbitTemplate, never()).sendAndReceive(any<String>(), any<String>(), any<Message>())
        }
    }

    // --- healthCheck ---------------------------------------------------

    @Nested
    inner class HealthCheck {

        @Test
        fun `successful statusRequest means the node is alive`() {
            whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
                .thenReturn(jsonReply(mapOf("type" to "statusResponse", "nodeId" to "n1")))

            assertTrue(adapter.healthCheck(node("n1")))
        }

        @Test
        fun `broker exception is swallowed as a 'not alive' verdict`() {
            whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
                .doThrow(RuntimeException("broker down"))

            assertFalse(adapter.healthCheck(node("n1")))
        }

        @Test
        fun `null reply is a 'not alive' verdict`() {
            whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
                .thenReturn(null)

            assertFalse(adapter.healthCheck(node("n1")))
        }
    }

    // --- updateConfig contract (envelope shape) -------------------------

    @Test
    fun `updateConfig wraps the payload as {type, nodeId, config} so the engine API recognises it`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(jsonReply(mapOf<String, Any?>("queueSize" to 0, "status" to "idle")))

        val cfg = ConfigUpdateRequest(maxCorrectnessWorkers = 2, defaultThreads = 4)
        adapter.updateConfig(node("n2"), cfg)

        val msg = argumentCaptor<Message>()
        verify(controlRabbitTemplate).sendAndReceive(
            eq(RabbitConstants.NODE_CONTROL_EXCHANGE), eq("n2"), msg.capture(),
        )
        val sent = objectMapper.readTree(msg.firstValue.body)
        assertEquals("updateConfig", sent.get("type").asText())
        assertEquals("n2", sent.get("nodeId").asText())
        // C-tests-runner expects the config nested under "config" - see
        // C-tests-runner/docs/claude/json-contract.md §3.10.
        assertTrue(sent.get("config") != null)
        assertEquals(2, sent.get("config").get("maxCorrectnessWorkers").asInt())
        assertEquals(4, sent.get("config").get("defaultThreads").asInt())
    }

    // --- error wrapping ------------------------------------------------

    @Test
    fun `arbitrary exception inside a control RPC surfaces as RabbitAdapterException with context`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .doThrow(RuntimeException("connection refused"))

        val ex = assertThrows<RabbitAdapterException> { adapter.queryNodeStatus(node("n3")) }
        // Original exception's message is included for diagnosability.
        assertContains(ex.message ?: "", "connection refused")
        assertContains(ex.message ?: "", "n3")
    }
}
