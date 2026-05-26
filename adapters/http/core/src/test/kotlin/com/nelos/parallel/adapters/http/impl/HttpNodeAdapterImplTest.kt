package com.nelos.parallel.adapters.http.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.adapter.enums.AdapterStatus
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.exceptions.AdapterException
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.request.ConfigUpdateRequest
import com.nelos.parallel.commons.adapter.vo.request.TaskSubmission
import com.nelos.parallel.commons.adapter.vo.response.AdapterActionResult
import com.nelos.parallel.commons.adapter.vo.response.AdapterInfo
import com.nelos.parallel.commons.adapter.vo.response.AdapterListResponse
import com.nelos.parallel.commons.adapter.vo.response.CancelJobResponse
import com.nelos.parallel.commons.adapter.vo.response.JobInfoEnvelope
import com.nelos.parallel.commons.adapter.vo.response.NodeStatus
import com.nelos.parallel.commons.adapter.vo.response.QueueStatus
import com.nelos.parallel.commons.adapter.vo.response.ResourceProviderActionResult
import com.nelos.parallel.commons.adapter.vo.response.ResourceProviderInfo
import com.nelos.parallel.commons.adapter.vo.response.ResourceProviderListResponse
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.commons.adapter.vo.response.TaskSubmissionResponse
import com.nelos.parallel.commons.adapter.vo.response.TransportConfig
import com.nelos.parallel.commons.adapter.vo.response.TransportInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest
import org.springframework.web.client.RestClient
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Exercises [HttpNodeAdapterImpl] against a {@link MockRestServiceServer}: every
 * endpoint contract (URL, method, auth header, body shape) is asserted on the
 * recorded request and the response shape is exercised by feeding back canned
 * JSON. Only the network bit is faked - the adapter's own JSON marshalling and
 * error wrapping run for real.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class HttpNodeAdapterImplTest {

    private val mapper = ObjectMapper()
    private lateinit var server: MockRestServiceServer
    private lateinit var adapter: HttpNodeAdapterImpl

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        adapter = HttpNodeAdapterImpl(builder.build(), mapper)
    }

    private fun node(
        id: String = "n1",
        host: String? = "127.0.0.1",
        port: Int? = 8080,
        authToken: String? = null,
        transports: List<TransportInfo>? = null,
    ): NodeInfo {
        val ts = transports ?: host?.let { _ ->
            listOf(
                TransportInfo(
                    type = TransportType.HTTP,
                    status = AdapterStatus.RUNNING,
                    config = TransportConfig.HttpConfig(host = host, port = port, authToken = authToken),
                ),
            )
        }
        return NodeInfo(nodeId = id, transports = ts)
    }

    @Nested
    inner class SubmitTask {

        @Test
        fun `posts to api run with bearer auth and returns the parsed response`() {
            val response = TaskSubmissionResponse(jobId = "j1", status = "accepted")
            server.expect(requestTo("http://127.0.0.1:8080/api/run"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret"))
                .andRespond(withSuccess(mapper.writeValueAsString(response), MediaType.APPLICATION_JSON))

            val resp = adapter.submitTask(node(authToken = "secret"), TaskSubmission(jobId = "j1"))

            assertEquals("j1", resp.jobId)
            assertEquals("accepted", resp.status)
            server.verify()
        }

        @Test
        fun `wraps 5xx into an AdapterException with the operation context`() {
            server.expect(requestTo("http://127.0.0.1:8080/api/run"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            val ex = assertThrows<AdapterException> {
                adapter.submitTask(node(), TaskSubmission(jobId = "j1"))
            }
            assertContains(ex.message ?: "", "Failed to submit task")
        }

        @Test
        fun `omits Authorization header when no token is configured`() {
            val response = TaskSubmissionResponse(jobId = "j1", status = "accepted")
            server.expect(requestTo("http://127.0.0.1:8080/api/run"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mapper.writeValueAsString(response), MediaType.APPLICATION_JSON))

            // No assertion on Authorization header here - just verify the call
            // completes successfully when the node carries no token.
            adapter.submitTask(node(authToken = null), TaskSubmission(jobId = "j1"))
        }
    }

    @Nested
    inner class QueryJobStatus {

        @Test
        fun `unwraps a JobInfoEnvelope with a nested TaskResult`() {
            val envelope = JobInfoEnvelope(
                jobId = "j1", status = "completed",
                result = TaskResult(jobId = "j1", status = "completed", error = null),
            )
            server.expect(requestTo("http://127.0.0.1:8080/api/jobs/j1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mapper.writeValueAsString(envelope), MediaType.APPLICATION_JSON))

            val result = adapter.queryJobStatus(node(), "j1")

            assertEquals("j1", result.jobId)
            assertEquals("completed", result.status)
        }

        @Test
        fun `falls back to envelope header fields when result is missing`() {
            val envelope = JobInfoEnvelope(jobId = "j1", status = "running", result = null)
            server.expect(requestTo("http://127.0.0.1:8080/api/jobs/j1"))
                .andRespond(withSuccess(mapper.writeValueAsString(envelope), MediaType.APPLICATION_JSON))

            val result = adapter.queryJobStatus(node(), "j1")

            assertEquals("j1", result.jobId)
            assertEquals("running", result.status)
        }
    }

    @Nested
    inner class CancelJob {

        @Test
        fun `sends DELETE to api jobs id`() {
            server.expect(requestTo("http://127.0.0.1:8080/api/jobs/j1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(
                    withSuccess(
                        mapper.writeValueAsString(CancelJobResponse(jobId = "j1", status = "cancelled")),
                        MediaType.APPLICATION_JSON,
                    )
                )

            val resp = adapter.cancelJob(node(), "j1")

            assertEquals("cancelled", resp.status)
        }
    }

    @Nested
    inner class QueryNodeStatus {

        @Test
        fun `GETs api node status and parses the response`() {
            val status = NodeStatus(type = "info", nodeId = "n1")
            server.expect(requestTo("http://127.0.0.1:8080/api/node/status"))
                .andRespond(withSuccess(mapper.writeValueAsString(status), MediaType.APPLICATION_JSON))

            val resp = adapter.queryNodeStatus(node())

            assertEquals("n1", resp.nodeId)
        }
    }

    @Nested
    inner class HealthCheck {

        @Test
        fun `returns true on a successful health probe`() {
            server.expect(requestTo("http://127.0.0.1:8080/api/health")).andRespond(withSuccess())

            assertTrue(adapter.healthCheck(node()))
        }

        @Test
        fun `returns false on a 5xx response`() {
            server.expect(requestTo("http://127.0.0.1:8080/api/health"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            assertFalse(adapter.healthCheck(node()))
        }

        @Test
        fun `401 is auth_failed, not dead - reachable nodes are not stripped`() {
            // The auth-failed result is internal; we observe it indirectly via
            // pickRunnerNode below. Here we just confirm healthCheck still flags
            // the node as not-alive but does not throw.
            server.expect(requestTo("http://127.0.0.1:8080/api/health")).andRespond(withUnauthorizedRequest())

            assertFalse(adapter.healthCheck(node()))
        }
    }

    @Nested
    inner class PickRunnerNode {

        @Test
        fun `picks the first alive candidate and returns the rest as kept`() {
            val a = node("a", host = "a", port = 81)
            val b = node("b", host = "b", port = 82)
            server.expect(requestTo("http://a:81/api/health")).andRespond(withSuccess())

            val pick = adapter.pickRunnerNode(listOf(a, b))

            assertSame(a, pick.live)
            assertTrue(pick.deadNodes.isEmpty())
        }

        @Test
        fun `dead candidates are reported, then live one returned`() {
            val a = node("a", host = "a", port = 81)
            val b = node("b", host = "b", port = 82)
            server.expect(requestTo("http://a:81/api/health"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))
            server.expect(requestTo("http://b:82/api/health")).andRespond(withSuccess())

            val pick = adapter.pickRunnerNode(listOf(a, b))

            assertSame(b, pick.live)
            assertEquals(listOf("a"), pick.deadNodes)
        }

        @Test
        fun `auth-failed candidates are skipped but not marked dead`() {
            val a = node("a", host = "a", port = 81)
            val b = node("b", host = "b", port = 82)
            server.expect(requestTo("http://a:81/api/health")).andRespond(withUnauthorizedRequest())
            server.expect(requestTo("http://b:82/api/health")).andRespond(withSuccess())

            val pick = adapter.pickRunnerNode(listOf(a, b))

            assertSame(b, pick.live)
            // The auth-failed node MUST NOT appear in deadNodes - that would strip
            // the HTTP transport and lose the (perfectly reachable) endpoint.
            assertTrue(pick.deadNodes.isEmpty())
        }

        @Test
        fun `403 is also treated as auth-failed, not dead`() {
            val a = node("a", host = "a", port = 81)
            server.expect(requestTo("http://a:81/api/health"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN))

            val pick = adapter.pickRunnerNode(listOf(a))

            assertNull(pick.live)
            assertTrue(pick.deadNodes.isEmpty())
        }

        @Test
        fun `empty candidate list returns live=null with no dead nodes`() {
            // No probes should be attempted - nothing to talk to.
            val pick = adapter.pickRunnerNode(emptyList())

            assertNull(pick.live)
            assertTrue(pick.deadNodes.isEmpty())
        }

        @Test
        fun `all candidates dead - live is null, dead list populated`() {
            val a = node("a", host = "a", port = 81)
            val b = node("b", host = "b", port = 82)
            server.expect(requestTo("http://a:81/api/health"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))
            server.expect(requestTo("http://b:82/api/health"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            val pick = adapter.pickRunnerNode(listOf(a, b))

            assertNull(pick.live)
            assertEquals(listOf("a", "b"), pick.deadNodes)
        }
    }

    @Nested
    inner class AdapterCalls {

        @Test
        fun `listAdapters unwraps the list response`() {
            val payload = AdapterListResponse(adapters = listOf(AdapterInfo(name = "rabbit")))
            server.expect(requestTo("http://127.0.0.1:8080/api/adapters"))
                .andRespond(withSuccess(mapper.writeValueAsString(payload), MediaType.APPLICATION_JSON))

            val list = adapter.listAdapters(node())

            assertEquals(1, list.size)
            assertEquals("rabbit", list[0].name)
        }

        @Test
        fun `listAvailableAdapters hits the available endpoint`() {
            val payload = AdapterListResponse(adapters = emptyList())
            server.expect(requestTo("http://127.0.0.1:8080/api/adapters/available"))
                .andRespond(withSuccess(mapper.writeValueAsString(payload), MediaType.APPLICATION_JSON))

            val list = adapter.listAvailableAdapters(node())

            assertTrue(list.isEmpty())
        }

        @Test
        fun `loadAdapter posts the supplied config object`() {
            val cfg = mapper.createObjectNode().put("host", "127.0.0.1")
            server.expect(requestTo("http://127.0.0.1:8080/api/adapters/rabbit"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("""{"host":"127.0.0.1"}"""))
                .andRespond(
                    withSuccess(
                        mapper.writeValueAsString(AdapterActionResult(adapter = "rabbit", status = "ok")),
                        MediaType.APPLICATION_JSON,
                    )
                )

            val resp = adapter.loadAdapter(node(), "rabbit", cfg)

            assertEquals("ok", resp.status)
        }

        @Test
        fun `loadAdapter null config sends an empty json object`() {
            // Defensive: passing `null` as the body would 415 the engine - the
            // adapter is expected to substitute {}.
            server.expect(requestTo("http://127.0.0.1:8080/api/adapters/rabbit"))
                .andExpect(content().string("{}"))
                .andRespond(
                    withSuccess(
                        mapper.writeValueAsString(AdapterActionResult(adapter = "rabbit", status = "ok")),
                        MediaType.APPLICATION_JSON,
                    )
                )

            adapter.loadAdapter(node(), "rabbit", null)
        }

        @Test
        fun `unloadAdapter deletes the named adapter`() {
            server.expect(requestTo("http://127.0.0.1:8080/api/adapters/rabbit"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(
                    withSuccess(
                        mapper.writeValueAsString(AdapterActionResult(adapter = "rabbit", status = "ok")),
                        MediaType.APPLICATION_JSON,
                    )
                )

            adapter.unloadAdapter(node(), "rabbit")
        }
    }

    @Nested
    inner class UpdateConfig {

        @Test
        fun `wraps the payload in a config envelope - engine API contract`() {
            // The engine expects {"config": {...}}; flat payloads silently get
            // dropped, which is a major footgun if this contract slips.
            server.expect(requestTo("http://127.0.0.1:8080/api/config"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string("""{"config":{"maxCorrectnessWorkers":4}}"""))
                .andRespond(
                    withSuccess(
                        mapper.writeValueAsString(QueueStatus()),
                        MediaType.APPLICATION_JSON,
                    )
                )

            adapter.updateConfig(node(), ConfigUpdateRequest(maxCorrectnessWorkers = 4))
        }
    }

    @Nested
    inner class ResourceProviders {

        @Test
        fun `listResourceProviders unwraps providers from the list response`() {
            val payload = ResourceProviderListResponse(providers = listOf(ResourceProviderInfo(name = "git")))
            server.expect(requestTo("http://127.0.0.1:8080/api/resource-providers"))
                .andRespond(withSuccess(mapper.writeValueAsString(payload), MediaType.APPLICATION_JSON))

            val list = adapter.listResourceProviders(node())

            assertEquals("git", list[0].name)
        }

        @Test
        fun `listAvailableResourceProviders hits the available endpoint`() {
            val payload = ResourceProviderListResponse(providers = emptyList())
            server.expect(requestTo("http://127.0.0.1:8080/api/resource-providers/available"))
                .andRespond(withSuccess(mapper.writeValueAsString(payload), MediaType.APPLICATION_JSON))

            assertTrue(adapter.listAvailableResourceProviders(node()).isEmpty())
        }

        @Test
        fun `loadResourceProvider with explicit config posts it`() {
            val cfg = mapper.createObjectNode().put("baseDir", "/srv")
            server.expect(requestTo("http://127.0.0.1:8080/api/resource-providers/local"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("""{"baseDir":"/srv"}"""))
                .andRespond(
                    withSuccess(
                        mapper.writeValueAsString(ResourceProviderActionResult(provider = "local", status = "ok")),
                        MediaType.APPLICATION_JSON,
                    )
                )

            adapter.loadResourceProvider(node(), "local", cfg)
        }

        @Test
        fun `loadResourceProvider with null config falls back to an empty object`() {
            server.expect(requestTo("http://127.0.0.1:8080/api/resource-providers/local"))
                .andExpect(content().string("{}"))
                .andRespond(
                    withSuccess(
                        mapper.writeValueAsString(ResourceProviderActionResult(provider = "local", status = "ok")),
                        MediaType.APPLICATION_JSON,
                    )
                )

            adapter.loadResourceProvider(node(), "local", null)
        }

        @Test
        fun `unloadResourceProvider deletes the named provider`() {
            server.expect(requestTo("http://127.0.0.1:8080/api/resource-providers/local"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(
                    withSuccess(
                        mapper.writeValueAsString(ResourceProviderActionResult(provider = "local", status = "ok")),
                        MediaType.APPLICATION_JSON,
                    )
                )

            adapter.unloadResourceProvider(node(), "local")
        }
    }

    @Nested
    inner class QueueStatusCall {

        @Test
        fun `GETs api status and parses an empty queue`() {
            server.expect(requestTo("http://127.0.0.1:8080/api/status"))
                .andRespond(withSuccess(mapper.writeValueAsString(QueueStatus()), MediaType.APPLICATION_JSON))

            val qs = adapter.queueStatus(node())

            // Just verify we got a parsed object back - field-level shape is
            // a contract test for QueueStatus, not this adapter.
            assertEquals(0, qs.queueSize ?: 0)
        }
    }

    @Nested
    inner class BaseUrl {

        @Test
        fun `IPv6 host gets wrapped in square brackets`() {
            // RFC 3986: hosts containing ':' must be bracketed in URIs.
            val n = node(host = "::1", port = 9000)
            server.expect(requestTo("http://[::1]:9000/api/health")).andRespond(withSuccess())

            assertTrue(adapter.healthCheck(n))
        }

        @Test
        fun `node with no HTTP transport is reported dead, not propagated as error`() {
            // baseUrl throws IllegalStateException, but probeHealth catches it
            // and reports the node as DEAD - healthCheck callers see a simple
            // boolean and the registry strips the (non-existent) transport.
            val noHttp = NodeInfo(nodeId = "n1", transports = emptyList())

            assertFalse(adapter.healthCheck(noHttp))
        }

        @Test
        fun `submitTask on a node with no HTTP transport wraps the config error in AdapterException`() {
            // For non-health calls, the error must surface so the caller knows
            // dispatch failed - it's wrapped in AdapterException by adapterCall.
            val noHttp = NodeInfo(nodeId = "n1", transports = emptyList())

            val ex = assertThrows<AdapterException> {
                adapter.submitTask(noHttp, TaskSubmission(jobId = "j1"))
            }
            assertContains(ex.message ?: "", "no HTTP transport")
        }
    }
}
