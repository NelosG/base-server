package com.nelos.parallel.pipeline.core.service.impl

import com.nelos.parallel.commons.adapter.*
import com.nelos.parallel.commons.adapter.enums.AdapterStatus
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.response.TransportConfig
import com.nelos.parallel.commons.adapter.vo.response.TransportInfo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class RunnerSelectorImplTest {

    private val nodeRegistry: NodeRegistry = mock()
    private val transportManager: NodeTransportManager = mock()
    private val adapterRegistry: NodeAdapterRegistry = mock()
    private val submissionLogger: SubmissionLogger = mock()

    private val selector = RunnerSelectorImpl(
        nodeRegistry, transportManager, adapterRegistry, submissionLogger,
    )

    // --- helpers --------------------------------------------------------

    private fun node(id: String, transport: TransportType, status: AdapterStatus? = AdapterStatus.RUNNING): NodeInfo {
        val config: TransportConfig = when (transport) {
            TransportType.HTTP -> TransportConfig.HttpConfig(host = "h", port = 8080)
            TransportType.AMQP -> TransportConfig.AmqpConfig(host = "rabbit", port = 5672)
        }
        return NodeInfo(
            nodeId = id,
            transports = listOf(TransportInfo(type = transport, status = status, config = config)),
        )
    }

    private fun adapter(type: TransportType, pick: RunnerPick = RunnerPick()): NodeAdapter = mock {
        on { transportType } doReturn type
        on { pickRunnerNode(any()) } doReturn pick
    }

    // --- cases ----------------------------------------------------------

    @Test
    fun `returns null when registry is empty and discovery finds nothing`() {
        whenever(nodeRegistry.findAll()).thenReturn(emptyList())
        whenever(transportManager.discoverAndRefresh(TransportType.AMQP)).thenReturn(emptyList())

        val result = selector.selectRunner(submissionId = 1L)

        assertNull(result)
        // selectOnce -> invalidate -> selectOnce -> discover -> null
        verify(nodeRegistry, times(1)).invalidateCache()
        verify(transportManager).discoverAndRefresh(TransportType.AMQP)
        verifyNoInteractions(submissionLogger)
    }

    @Test
    fun `picks the first usable node on the first attempt`() {
        val amqpNode = node("amqp-1", TransportType.AMQP)
        val amqpAdapter = adapter(TransportType.AMQP, pick = RunnerPick(live = amqpNode))
        whenever(nodeRegistry.findAll()).thenReturn(listOf(amqpNode))
        whenever(adapterRegistry.adaptersInPreferenceOrder).thenReturn(listOf(amqpAdapter))

        val result = selector.selectRunner(2L)

        assertSame(amqpNode, result?.node)
        assertSame(amqpAdapter, result?.adapter)
        assertEquals(TransportType.AMQP, result?.transport)
        verify(nodeRegistry, never()).invalidateCache()
        verify(transportManager, never()).discoverAndRefresh(any())
    }

    @Test
    fun `retries after cache invalidate when first attempt comes up empty`() {
        val httpNode = node("http-1", TransportType.HTTP)
        val httpAdapter = adapter(TransportType.HTTP, pick = RunnerPick(live = httpNode))
        whenever(nodeRegistry.findAll())
            .thenReturn(emptyList())   // first selectOnce
            .thenReturn(listOf(httpNode)) // after invalidateCache
        whenever(adapterRegistry.adaptersInPreferenceOrder).thenReturn(listOf(httpAdapter))

        val result = selector.selectRunner(3L)

        assertSame(httpNode, result?.node)
        verify(nodeRegistry).invalidateCache()
        verify(transportManager, never()).discoverAndRefresh(any())
    }

    @Test
    fun `falls back to AMQP discovery when both cached lookups fail`() {
        val amqpNode = node("amqp-2", TransportType.AMQP)
        val amqpAdapter = adapter(TransportType.AMQP, pick = RunnerPick(live = amqpNode))
        whenever(nodeRegistry.findAll())
            .thenReturn(emptyList())       // selectOnce 1
            .thenReturn(emptyList())       // after invalidate
            .thenReturn(listOf(amqpNode))  // after discover
        whenever(transportManager.discoverAndRefresh(TransportType.AMQP))
            .thenReturn(listOf(amqpNode))
        whenever(adapterRegistry.adaptersInPreferenceOrder).thenReturn(listOf(amqpAdapter))

        val result = selector.selectRunner(4L)

        assertSame(amqpNode, result?.node)
        verify(submissionLogger).appendOne(eq(4L), argThat<String> { contains("AMQP discovery picked up 1 node") })
    }

    @Test
    fun `nodes whose transport status is STOPPED or FAILED are filtered out`() {
        val dead = node("dead", TransportType.HTTP, status = AdapterStatus.STOPPED)
        val live = node("live", TransportType.HTTP, status = AdapterStatus.RUNNING)
        val httpAdapter = adapter(TransportType.HTTP, pick = RunnerPick(live = live))
        whenever(nodeRegistry.findAll()).thenReturn(listOf(dead, live))
        whenever(adapterRegistry.adaptersInPreferenceOrder).thenReturn(listOf(httpAdapter))

        selector.selectRunner(5L)

        // The adapter must only be offered the live candidate.
        val captor = argumentCaptor<List<NodeInfo>>()
        verify(httpAdapter).pickRunnerNode(captor.capture())
        assertEquals(listOf("live"), captor.firstValue.map { it.nodeId })
    }

    @Test
    fun `dead nodes reported by the adapter are forwarded to transportManager and logged`() {
        val n1 = node("n1", TransportType.HTTP)
        val n2 = node("n2", TransportType.HTTP)
        val httpAdapter = adapter(
            TransportType.HTTP,
            pick = RunnerPick(live = n2, deadNodes = listOf("n1")),
        )
        whenever(nodeRegistry.findAll()).thenReturn(listOf(n1, n2))
        whenever(adapterRegistry.adaptersInPreferenceOrder).thenReturn(listOf(httpAdapter))

        selector.selectRunner(7L)

        verify(transportManager).handleHealthCheckFailure("n1", TransportType.HTTP)
        verify(submissionLogger).appendOne(eq(7L), argThat<String> {
            contains("Removed HTTP from 1 unresponsive node")
        })
    }

    @Test
    fun `adapter iteration stops at the first one that yields a pick`() {
        val amqpNode = node("a", TransportType.AMQP)
        val httpNode = node("h", TransportType.HTTP)
        val amqpAdapter = adapter(TransportType.AMQP, pick = RunnerPick(live = amqpNode))
        val httpAdapter = adapter(TransportType.HTTP, pick = RunnerPick(live = httpNode))
        whenever(nodeRegistry.findAll()).thenReturn(listOf(amqpNode, httpNode))
        // AMQP first per preference order.
        whenever(adapterRegistry.adaptersInPreferenceOrder).thenReturn(listOf(amqpAdapter, httpAdapter))

        val result = selector.selectRunner(8L)

        assertSame(amqpNode, result?.node)
        verify(amqpAdapter).pickRunnerNode(any())
        verify(httpAdapter, never()).pickRunnerNode(any())
    }
}
