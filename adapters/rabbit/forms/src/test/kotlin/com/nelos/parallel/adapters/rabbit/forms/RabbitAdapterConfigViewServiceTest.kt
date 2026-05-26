package com.nelos.parallel.adapters.rabbit.forms

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.NodeTransportManager
import com.nelos.parallel.commons.adapter.enums.TransportType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class RabbitAdapterConfigViewServiceTest {

    private val nodeRegistry: NodeRegistry = mock()
    private val nodeAdapter: NodeAdapter = mock()
    private val transportManager: NodeTransportManager = mock()
    private val mapper = ObjectMapper()

    private val service = RabbitAdapterConfigViewService(nodeRegistry, mapper, transportManager, nodeAdapter)

    @Test
    fun `refreshAndPruneNodes triggers on-demand AMQP discovery, not health-poll`() {
        // AMQP discovery is fan-out via a control message - the base class's
        // per-node healthCheck loop would never hit the broker in time, so the
        // subclass overrides the refresh path entirely.
        whenever(nodeRegistry.findByTransport(TransportType.AMQP)).thenReturn(emptyList())

        service.refreshAndPruneNodes()

        verify(transportManager).discoverAndRefresh(TransportType.AMQP)
        // The base class's "filter then healthCheck" path must NOT run.
        verify(nodeAdapter, never()).healthCheck(org.mockito.kotlin.any())
    }
}
