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

    @Test
    fun `null reply from control RPC raises a RabbitAdapterException`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(null)

        val ex = assertThrows<RabbitAdapterException> { adapter.queryNodeStatus(node()) }
        assertContains(ex.message ?: "", "No response")
    }

    // --- pass-through control RPCs (single-shape sanity checks) ---------

    @Test
    fun `queryJobStatus uses getJobInfo control message and unwraps the envelope`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(jsonReply(
                com.nelos.parallel.commons.adapter.vo.response.JobInfoEnvelope(
                    jobId = "j1", status = "running", result = null,
                ),
            ))

        val result = adapter.queryJobStatus(node("n1"), "j1")

        assertEquals("j1", result.jobId)
        assertEquals("running", result.status)
        val msg = argumentCaptor<Message>()
        verify(controlRabbitTemplate).sendAndReceive(eq(RabbitConstants.NODE_CONTROL_EXCHANGE), eq("n1"), msg.capture())
        val body = objectMapper.readTree(msg.firstValue.body)
        assertEquals("getJobInfo", body.get("type").asText())
        assertEquals("j1", body.get("jobId").asText())
    }

    @Test
    fun `cancelJob sends cancelJob control message and parses the reply`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(jsonReply(
                com.nelos.parallel.commons.adapter.vo.response.CancelJobResponse(
                    jobId = "j1", status = "cancelled",
                ),
            ))

        val resp = adapter.cancelJob(node(), "j1")

        assertEquals("cancelled", resp.status)
    }

    @Test
    fun `queueStatus sends queueStatus control message`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(jsonReply(com.nelos.parallel.commons.adapter.vo.response.QueueStatus(queueSize = 5)))

        val resp = adapter.queueStatus(node())

        assertEquals(5, resp.queueSize)
    }

    @Test
    fun `listAdapters unwraps the adapters field from the control reply`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(jsonReply(mapOf(
                "adapters" to listOf(
                    com.nelos.parallel.commons.adapter.vo.response.AdapterInfo(name = "rabbit"),
                ),
            )))

        val list = adapter.listAdapters(node())

        assertEquals(listOf("rabbit"), list.map { it.name })
    }

    @Test
    fun `listAdapters reply without an adapters field is rejected`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(jsonReply(mapOf("something-else" to "x")))

        val ex = assertThrows<RabbitAdapterException> { adapter.listAdapters(node()) }
        assertContains(ex.message ?: "", "adapters")
    }

    @Test
    fun `loadAdapter wraps the config as a nested object in the control payload`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(jsonReply(
                com.nelos.parallel.commons.adapter.vo.response.AdapterActionResult(adapter = "rabbit", status = "ok"),
            ))
        val cfg = objectMapper.createObjectNode().put("host", "r")

        adapter.loadAdapter(node(), "rabbit", cfg)

        val msg = argumentCaptor<Message>()
        verify(controlRabbitTemplate).sendAndReceive(any<String>(), any<String>(), msg.capture())
        val body = objectMapper.readTree(msg.firstValue.body)
        assertEquals("loadAdapter", body.get("type").asText())
        assertEquals("rabbit", body.get("adapter").asText())
        assertEquals("r", body.get("config").get("host").asText())
    }

    @Test
    fun `loadAdapter with null config substitutes an empty object - matches HTTP semantics`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(jsonReply(
                com.nelos.parallel.commons.adapter.vo.response.AdapterActionResult(adapter = "rabbit", status = "ok"),
            ))

        adapter.loadAdapter(node(), "rabbit", config = null)

        val msg = argumentCaptor<Message>()
        verify(controlRabbitTemplate).sendAndReceive(any<String>(), any<String>(), msg.capture())
        val body = objectMapper.readTree(msg.firstValue.body)
        assertTrue(body.get("config").isObject)
        assertTrue(body.get("config").isEmpty)
    }

    @Test
    fun `unloadAdapter sends unloadAdapter with the adapter name`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(jsonReply(
                com.nelos.parallel.commons.adapter.vo.response.AdapterActionResult(adapter = "rabbit", status = "ok"),
            ))

        adapter.unloadAdapter(node(), "rabbit")

        val msg = argumentCaptor<Message>()
        verify(controlRabbitTemplate).sendAndReceive(any<String>(), any<String>(), msg.capture())
        val body = objectMapper.readTree(msg.firstValue.body)
        assertEquals("unloadAdapter", body.get("type").asText())
        assertEquals("rabbit", body.get("adapter").asText())
    }

    @Test
    fun `listResourceProviders unwraps the providers field`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(jsonReply(mapOf(
                "providers" to listOf(
                    com.nelos.parallel.commons.adapter.vo.response.ResourceProviderInfo(name = "git"),
                ),
            )))

        val list = adapter.listResourceProviders(node())

        assertEquals(listOf("git"), list.map { it.name })
    }

    @Test
    fun `listResourceProviders reply without a providers field is rejected`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(jsonReply(mapOf("nothing" to true)))

        val ex = assertThrows<RabbitAdapterException> { adapter.listResourceProviders(node()) }
        assertContains(ex.message ?: "", "providers")
    }

    @Test
    fun `loadResourceProvider wires the provider name and nested config`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(jsonReply(
                com.nelos.parallel.commons.adapter.vo.response.ResourceProviderActionResult(provider = "local", status = "ok"),
            ))
        val cfg = objectMapper.createObjectNode().put("baseDir", "/srv")

        adapter.loadResourceProvider(node(), "local", cfg)

        val msg = argumentCaptor<Message>()
        verify(controlRabbitTemplate).sendAndReceive(any<String>(), any<String>(), msg.capture())
        val body = objectMapper.readTree(msg.firstValue.body)
        assertEquals("loadResourceProvider", body.get("type").asText())
        assertEquals("local", body.get("provider").asText())
        assertEquals("/srv", body.get("config").get("baseDir").asText())
    }

    @Test
    fun `unloadResourceProvider sends provider name in the control body`() {
        whenever(controlRabbitTemplate.sendAndReceive(any<String>(), any<String>(), any<Message>()))
            .thenReturn(jsonReply(
                com.nelos.parallel.commons.adapter.vo.response.ResourceProviderActionResult(provider = "local", status = "ok"),
            ))

        adapter.unloadResourceProvider(node(), "local")

        val msg = argumentCaptor<Message>()
        verify(controlRabbitTemplate).sendAndReceive(any<String>(), any<String>(), msg.capture())
        val body = objectMapper.readTree(msg.firstValue.body)
        assertEquals("unloadResourceProvider", body.get("type").asText())
        assertEquals("local", body.get("provider").asText())
    }

}
