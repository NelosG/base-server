package com.nelos.parallel.adapters.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.adapters.config.vo.AdapterRequest
import com.nelos.parallel.adapters.config.vo.ConfigRequest
import com.nelos.parallel.adapters.config.vo.ResourceProviderRequest
import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.NodeTransportManager
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.request.ConfigUpdateRequest
import com.nelos.parallel.commons.adapter.vo.response.AdapterActionResult
import com.nelos.parallel.commons.adapter.vo.response.AdapterInfo
import com.nelos.parallel.commons.adapter.vo.response.NodeStatus
import com.nelos.parallel.commons.adapter.vo.response.QueueStatus
import com.nelos.parallel.commons.adapter.vo.response.ResourceProviderActionResult
import com.nelos.parallel.commons.adapter.vo.response.ResourceProviderInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.nullableArgumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the shared adapter-config logic via a minimal test-only subclass that
 * pins the transport type and adapter. Only behavior owned by the abstract
 * base is exercised here; the HTTP-specific and AMQP-specific subclasses have
 * their own tests.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class AbstractAdapterConfigViewServiceTest {

    private val nodeRegistry: NodeRegistry = mock()
    private val nodeAdapter: NodeAdapter = mock()
    private val transportManager: NodeTransportManager = mock()
    private val mapper = ObjectMapper()

    private val service = object : AbstractAdapterConfigViewService(nodeRegistry, mapper, transportManager) {
        override val transportType: TransportType = TransportType.HTTP
        override val log: Logger = LoggerFactory.getLogger("test")
        override val adapter: NodeAdapter = nodeAdapter
    }

    private fun node(id: String = "n1") = NodeInfo(nodeId = id, transports = emptyList())

    // ---- getNodes / refreshAndPruneNodes ------------------------------------

    @Test
    fun `getNodes maps registered nodes to view objects scoped to the transport`() {
        whenever(nodeRegistry.findByTransport(TransportType.HTTP))
            .thenReturn(listOf(node("a"), node("b")))

        val views = service.getNodes()

        assertEquals(listOf("a", "b"), views.map { it.nodeId })
        verify(nodeRegistry, never()).findByTransport(TransportType.AMQP)
    }

    @Test
    fun `refreshAndPruneNodes strips transport from nodes that fail health check`() {
        val alive = node("alive")
        val dead = node("dead")
        whenever(nodeRegistry.findByTransport(TransportType.HTTP)).thenReturn(listOf(alive, dead))
        whenever(nodeAdapter.healthCheck(alive)).thenReturn(true)
        whenever(nodeAdapter.healthCheck(dead)).thenReturn(false)

        service.refreshAndPruneNodes()

        verify(transportManager).handleHealthCheckFailure("dead", TransportType.HTTP)
        verify(transportManager, never()).handleHealthCheckFailure(eq("alive"), any())
    }

    @Test
    fun `refreshAndPruneNodes treats a healthCheck exception as a failure`() {
        val node = node("flaky")
        whenever(nodeRegistry.findByTransport(TransportType.HTTP)).thenReturn(listOf(node))
        whenever(nodeAdapter.healthCheck(node)).doThrow(RuntimeException("timeout"))

        service.refreshAndPruneNodes()

        verify(transportManager).handleHealthCheckFailure("flaky", TransportType.HTTP)
    }

    // ---- healthCheck (single node) -----------------------------------------

    @Test
    fun `healthCheck returns the adapter result wrapped in a view`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.healthCheck(n)).thenReturn(true)

        val view = service.healthCheck("n1")

        assertEquals("n1", view.nodeId)
        assertTrue(view.healthy)
    }

    @Test
    fun `healthCheck on an unknown node raises an argument error`() {
        whenever(nodeRegistry.findById("ghost")).thenReturn(null)

        val ex = assertThrows<IllegalArgumentException> { service.healthCheck("ghost") }
        assertEquals("Node not found: ghost", ex.message)
    }

    @Test
    fun `healthCheck false when adapter says node is down`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.healthCheck(n)).thenReturn(false)

        assertFalse(service.healthCheck("n1").healthy)
    }

    // ---- pollNodeStatus / queueStatus --------------------------------------

    @Test
    fun `queueStatus enriches the queue payload with engine config if available`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.queueStatus(n)).thenReturn(QueueStatus(queueSize = 3, activeJobs = 1))
        whenever(nodeAdapter.queryNodeStatus(n)).thenReturn(NodeStatus(nodeId = "n1", type = "info"))

        val view = service.queueStatus("n1")

        assertEquals(3, view.queueSize)
        assertEquals(1, view.activeJobs)
    }

    @Test
    fun `queueStatus tolerates an engine-config probe failure`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.queueStatus(n)).thenReturn(QueueStatus(queueSize = 1))
        whenever(nodeAdapter.queryNodeStatus(n)).doThrow(RuntimeException("control RPC failed"))

        val view = service.queueStatus("n1")

        assertEquals(1, view.queueSize)
        assertNull(view.engineConfig)
    }

    // ---- removeNode --------------------------------------------------------

    @Test
    fun `removeNode reports the deregistration result returned by the registry`() {
        whenever(nodeRegistry.deregister("n1")).thenReturn(true)

        val view = service.removeNode("n1")

        assertEquals("n1", view.nodeId)
        assertTrue(view.removed)
    }

    @Test
    fun `removeNode reports false when registry says no such node`() {
        whenever(nodeRegistry.deregister("ghost")).thenReturn(false)

        assertFalse(service.removeNode("ghost").removed)
    }

    // ---- updateConfig ------------------------------------------------------

    @Test
    fun `updateConfig forwards all seven knobs to the adapter`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.updateConfig(eq(n), any())).thenReturn(QueueStatus())
        whenever(nodeAdapter.queueStatus(n)).thenReturn(QueueStatus())
        whenever(nodeAdapter.queryNodeStatus(n)).thenReturn(NodeStatus(nodeId = "n1", type = "info"))

        val request = ConfigRequest(
            nodeId = "n1",
            maxCorrectnessWorkers = 4,
            jobRetentionSeconds = 60,
            defaultMemoryLimitMb = 512,
            defaultThreads = 8,
            defaultWallTimeSec = 30,
            defaultCpuTimeSec = 10,
            sandboxProcessMultiplier = 3,
        )
        service.updateConfig(request)

        val captor = argumentCaptor<ConfigUpdateRequest>()
        verify(nodeAdapter).updateConfig(eq(n), captor.capture())
        val sent = captor.firstValue
        assertEquals(4, sent.maxCorrectnessWorkers)
        assertEquals(60, sent.jobRetentionSeconds)
        assertEquals(512L, sent.defaultMemoryLimitMb)
        assertEquals(8, sent.defaultThreads)
        assertEquals(30, sent.defaultWallTimeSec)
        assertEquals(10, sent.defaultCpuTimeSec)
        assertEquals(3, sent.sandboxProcessMultiplier)
    }

    @Test
    fun `updateConfig without nodeId is rejected`() {
        assertThrows<IllegalArgumentException> { service.updateConfig(ConfigRequest(nodeId = null)) }
        verify(nodeAdapter, never()).updateConfig(any(), any())
    }

    // ---- adapter plugin load / unload --------------------------------------

    @Test
    fun `listAdapters and listAvailableAdapters delegate to their respective adapter calls`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.listAdapters(n)).thenReturn(listOf(AdapterInfo(name = "rabbit")))
        whenever(nodeAdapter.listAvailableAdapters(n)).thenReturn(listOf(AdapterInfo(name = "http")))

        assertEquals(listOf("rabbit"), service.listAdapters("n1").map { it.name })
        assertEquals(listOf("http"), service.listAvailableAdapters("n1").map { it.name })
    }

    @Test
    fun `loadAdapter parses a non-empty config string and forwards it`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.loadAdapter(eq(n), eq("rabbit"), any()))
            .thenReturn(AdapterActionResult(adapter = "rabbit", status = "ok"))

        val view = service.loadAdapter(
            AdapterRequest(nodeId = "n1", adapterName = "rabbit", config = """{"host":"r"}"""),
        )

        assertEquals("ok", view.status)
        val captor = nullableArgumentCaptor<com.fasterxml.jackson.databind.node.ObjectNode>()
        verify(nodeAdapter).loadAdapter(eq(n), eq("rabbit"), captor.capture())
        val cfg = captor.firstValue
        assertNotNull(cfg)
        assertEquals("r", cfg.get("host").asText())
    }

    @Test
    fun `loadAdapter with empty config string sends null - engine substitutes defaults`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.loadAdapter(eq(n), eq("rabbit"), isNull()))
            .thenReturn(AdapterActionResult(adapter = "rabbit", status = "ok"))

        service.loadAdapter(AdapterRequest(nodeId = "n1", adapterName = "rabbit", config = "{}"))

        verify(nodeAdapter).loadAdapter(eq(n), eq("rabbit"), isNull())
    }

    @Test
    fun `loadAdapter with malformed JSON silently drops the config`() {
        // Cosmetic UI safety: bad JSON in the editor shouldn't 500 - the adapter
        // gets null and the engine handles it like an empty config.
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.loadAdapter(eq(n), eq("rabbit"), isNull()))
            .thenReturn(AdapterActionResult(adapter = "rabbit", status = "ok"))

        service.loadAdapter(AdapterRequest(nodeId = "n1", adapterName = "rabbit", config = "not-json"))

        verify(nodeAdapter).loadAdapter(eq(n), eq("rabbit"), isNull())
    }

    @Test
    fun `loadAdapter without nodeId or adapterName is rejected`() {
        assertThrows<IllegalArgumentException> {
            service.loadAdapter(AdapterRequest(nodeId = null, adapterName = "x"))
        }
        assertThrows<IllegalArgumentException> {
            service.loadAdapter(AdapterRequest(nodeId = "n1", adapterName = null))
        }
    }

    @Test
    fun `unloadAdapter requires both ids and propagates the action result`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.unloadAdapter(n, "rabbit"))
            .thenReturn(AdapterActionResult(adapter = "rabbit", status = "ok"))

        val view = service.unloadAdapter(AdapterRequest(nodeId = "n1", adapterName = "rabbit"))

        assertEquals("ok", view.status)
    }

    // ---- resource providers ------------------------------------------------

    @Test
    fun `listResourceProviders maps each provider to a view`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.listResourceProviders(n))
            .thenReturn(listOf(ResourceProviderInfo(name = "git")))

        val list = service.listResourceProviders("n1")

        assertEquals(listOf("git"), list.map { it.name })
    }

    @Test
    fun `listAvailableResourceProviders maps each provider to a view`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.listAvailableResourceProviders(n))
            .thenReturn(listOf(ResourceProviderInfo(name = "local")))

        val list = service.listAvailableResourceProviders("n1")

        assertEquals(listOf("local"), list.map { it.name })
    }

    @Test
    fun `loadResourceProvider parses config and forwards it`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.loadResourceProvider(eq(n), eq("local"), any()))
            .thenReturn(ResourceProviderActionResult(provider = "local", status = "ok"))

        val view = service.loadResourceProvider(
            ResourceProviderRequest(nodeId = "n1", providerName = "local", config = """{"baseDir":"/srv"}"""),
        )

        assertEquals("ok", view.status)
    }

    @Test
    fun `loadResourceProvider with empty config string sends null - engine substitutes defaults`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.loadResourceProvider(eq(n), eq("local"), isNull()))
            .thenReturn(ResourceProviderActionResult(provider = "local", status = "ok"))

        service.loadResourceProvider(
            ResourceProviderRequest(nodeId = "n1", providerName = "local", config = "{}"),
        )

        verify(nodeAdapter).loadResourceProvider(eq(n), eq("local"), isNull())
    }

    @Test
    fun `loadResourceProvider with malformed JSON silently drops the config`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.loadResourceProvider(eq(n), eq("local"), isNull()))
            .thenReturn(ResourceProviderActionResult(provider = "local", status = "ok"))

        service.loadResourceProvider(
            ResourceProviderRequest(nodeId = "n1", providerName = "local", config = "not-json"),
        )

        verify(nodeAdapter).loadResourceProvider(eq(n), eq("local"), isNull())
    }

    @Test
    fun `loadResourceProvider with no nodeId or providerName is rejected`() {
        assertThrows<IllegalArgumentException> {
            service.loadResourceProvider(ResourceProviderRequest(nodeId = null, providerName = "local"))
        }
        assertThrows<IllegalArgumentException> {
            service.loadResourceProvider(ResourceProviderRequest(nodeId = "n1", providerName = null))
        }
    }

    @Test
    fun `unloadResourceProvider propagates the adapter result`() {
        val n = node()
        whenever(nodeRegistry.findById("n1")).thenReturn(n)
        whenever(nodeAdapter.unloadResourceProvider(n, "local"))
            .thenReturn(ResourceProviderActionResult(provider = "local", status = "ok"))

        val view = service.unloadResourceProvider(
            ResourceProviderRequest(nodeId = "n1", providerName = "local"),
        )

        assertEquals("ok", view.status)
    }

    @Test
    fun `unloadResourceProvider without ids is rejected`() {
        assertThrows<IllegalArgumentException> {
            service.unloadResourceProvider(ResourceProviderRequest(nodeId = null, providerName = "local"))
        }
        assertThrows<IllegalArgumentException> {
            service.unloadResourceProvider(ResourceProviderRequest(nodeId = "n1", providerName = null))
        }
    }
}
