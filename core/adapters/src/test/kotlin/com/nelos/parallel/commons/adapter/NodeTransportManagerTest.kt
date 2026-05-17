package com.nelos.parallel.commons.adapter

import com.nelos.parallel.commons.adapter.enums.AdapterStatus
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.response.NodeCapabilities
import com.nelos.parallel.commons.adapter.vo.response.NodeStatus
import com.nelos.parallel.commons.adapter.vo.response.TransportConfig
import com.nelos.parallel.commons.adapter.vo.response.TransportInfo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class NodeTransportManagerTest {

    private val nodeRegistry: NodeRegistry = mock()
    private val adapterRegistry: NodeAdapterRegistry = mock()
    private val manager = NodeTransportManager(nodeRegistry, adapterRegistry)

    // --- helpers --------------------------------------------------------

    private fun transportInfo(type: TransportType, status: AdapterStatus = AdapterStatus.RUNNING): TransportInfo {
        val config: TransportConfig = when (type) {
            TransportType.HTTP -> TransportConfig.HttpConfig(host = "h", port = 80)
            TransportType.AMQP -> TransportConfig.AmqpConfig(host = "rabbit", port = 5672)
        }
        return TransportInfo(type = type, status = status, config = config)
    }

    private fun node(
        id: String,
        transports: List<TransportInfo>,
        registeredAt: Instant = Instant.now(),
    ) = NodeInfo(nodeId = id, transports = transports, registeredAt = registeredAt)

    // --- handleTransportOffline -----------------------------------------

    @Test
    fun `removes the whole node when no transports remain after offline`() {
        whenever(nodeRegistry.removeTransport("n1", TransportType.HTTP)).thenReturn(null)

        val removed = manager.handleTransportOffline("n1", TransportType.HTTP)

        assertTrue(removed)
        // Bare removal path - the manager should not even query an adapter.
        verify(adapterRegistry, never()).findAdapter(any())
    }

    @Test
    fun `deregisters node when remaining transports have no usable status`() {
        val stopped = node(
            "n1",
            transports = listOf(transportInfo(TransportType.AMQP, status = AdapterStatus.STOPPED)),
        )
        whenever(nodeRegistry.removeTransport("n1", TransportType.HTTP)).thenReturn(stopped)

        val removed = manager.handleTransportOffline("n1", TransportType.HTTP)

        assertTrue(removed)
        verify(nodeRegistry).deregister("n1")
    }

    @Test
    fun `refreshes node when at least one remaining transport responds`() {
        val remaining = node(
            "n1",
            transports = listOf(transportInfo(TransportType.AMQP)),
        )
        whenever(nodeRegistry.removeTransport("n1", TransportType.HTTP)).thenReturn(remaining)
        val amqpAdapter: NodeAdapter = mock {
            on { queryNodeStatus(any()) } doReturn NodeStatus(
                type = "info", nodeId = "n1",
                capabilities = NodeCapabilities(maxConcurrentCorrectness = 4, maxThreads = 8),
                resourceProviders = null,
            )
        }
        whenever(adapterRegistry.findAdapter(TransportType.AMQP)).thenReturn(amqpAdapter)

        val removed = manager.handleTransportOffline("n1", TransportType.HTTP)

        assertFalse(removed)
        val updated = argumentCaptor<NodeInfo>()
        verify(nodeRegistry).updateNode(updated.capture())
        // Capabilities from the live response replace the previous ones.
        assertTrue(updated.firstValue.capabilities?.maxConcurrentCorrectness == 4)
        verify(nodeRegistry, never()).deregister("n1")
    }

    @Test
    fun `deregisters node when none of the remaining transports respond`() {
        val remaining = node(
            "n1",
            transports = listOf(transportInfo(TransportType.AMQP)),
        )
        whenever(nodeRegistry.removeTransport("n1", TransportType.HTTP)).thenReturn(remaining)
        whenever(nodeRegistry.findById("n1")).thenReturn(null) // not re-registered during probe
        val amqpAdapter: NodeAdapter = mock {
            on { queryNodeStatus(any()) } doThrow RuntimeException("no reply")
        }
        whenever(adapterRegistry.findAdapter(TransportType.AMQP)).thenReturn(amqpAdapter)

        val removed = manager.handleTransportOffline("n1", TransportType.HTTP)

        assertTrue(removed)
        verify(nodeRegistry).deregister("n1")
    }

    @Test
    fun `race guard aborts deregister when node re-registered during the probe`() {
        val originalRegisteredAt = Instant.now().minusSeconds(60)
        val remaining = node(
            "n1",
            transports = listOf(transportInfo(TransportType.AMQP)),
            registeredAt = originalRegisteredAt,
        )
        whenever(nodeRegistry.removeTransport("n1", TransportType.HTTP)).thenReturn(remaining)
        val amqpAdapter: NodeAdapter = mock {
            on { queryNodeStatus(any()) } doThrow RuntimeException("no reply")
        }
        whenever(adapterRegistry.findAdapter(TransportType.AMQP)).thenReturn(amqpAdapter)
        // A fresh registration shows up while we were probing.
        whenever(nodeRegistry.findById("n1")).thenReturn(
            node("n1", listOf(transportInfo(TransportType.HTTP)), registeredAt = Instant.now()),
        )

        val removed = manager.handleTransportOffline("n1", TransportType.HTTP)

        assertFalse(removed)
        verify(nodeRegistry, never()).deregister("n1")
    }

    // --- handleHealthCheckFailure ---------------------------------------

    @Test
    fun `health-check failure removes only the failed transport when others remain`() {
        whenever(nodeRegistry.removeTransport("n1", TransportType.HTTP))
            .thenReturn(node("n1", listOf(transportInfo(TransportType.AMQP))))

        val removed = manager.handleHealthCheckFailure("n1", TransportType.HTTP)

        assertFalse(removed)
        verify(nodeRegistry, never()).deregister(any())
    }

    @Test
    fun `health-check failure removes node when no transports remain`() {
        whenever(nodeRegistry.removeTransport("n1", TransportType.HTTP)).thenReturn(null)

        val removed = manager.handleHealthCheckFailure("n1", TransportType.HTTP)

        assertTrue(removed)
    }

    // --- discoverAndRefresh ---------------------------------------------

    @Test
    fun `discover returns empty when no adapter is registered for the transport`() {
        whenever(adapterRegistry.findAdapter(TransportType.AMQP)).thenReturn(null)

        val result = manager.discoverAndRefresh(TransportType.AMQP)

        assertTrue(result.isEmpty())
        verify(nodeRegistry, never()).register(any())
    }

    @Test
    fun `discover registers responding nodes and strips transport from silent ones`() {
        val responding = node("alive", listOf(transportInfo(TransportType.AMQP)))
        val silentRegistered = node("ghost", listOf(transportInfo(TransportType.AMQP)))
        val adapter: NodeAdapter = mock {
            on { discoverNodes() } doReturn listOf(responding)
        }
        whenever(adapterRegistry.findAdapter(TransportType.AMQP)).thenReturn(adapter)
        whenever(nodeRegistry.findByTransport(TransportType.AMQP)).thenReturn(listOf(silentRegistered))
        whenever(nodeRegistry.removeTransport("ghost", TransportType.AMQP)).thenReturn(null)

        val result = manager.discoverAndRefresh(TransportType.AMQP)

        assertTrue(result.size == 1)
        verify(nodeRegistry).register(responding)
        verify(nodeRegistry).removeTransport(eq("ghost"), eq(TransportType.AMQP))
    }
}
