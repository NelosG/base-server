package com.nelos.parallel.adapters.http.controllers

import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.NodeTransportManager
import com.nelos.parallel.commons.adapter.enums.NodeEventType
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.request.NodeRegistrationRequest
import com.nelos.parallel.commons.adapter.vo.response.TransportConfig
import com.nelos.parallel.commons.adapter.vo.response.TransportInfo
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class NodeRegistrationControllerTest {

    private val nodeRegistry: NodeRegistry = mock()
    private val transportManager: NodeTransportManager = mock()
    private val controller = NodeRegistrationController(nodeRegistry, transportManager)

    private fun httpTransport(host: String? = "127.0.0.1", port: Int? = 8080) =
        TransportInfo(
            type = TransportType.HTTP,
            config = TransportConfig.HttpConfig(host = host, port = port),
        )

    @Test
    fun `blank nodeId is rejected with 400`() {
        val response = controller.register(NodeRegistrationRequest(nodeId = ""))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("error", response.body?.status)
        verify(nodeRegistry, never()).register(any())
    }

    @Test
    fun `ONLINE without an HTTP transport is rejected`() {
        // AMQP-only registrations must come from the broker discovery path,
        // not /api/register.
        val request = NodeRegistrationRequest(
            nodeId = "n1",
            type = NodeEventType.ONLINE,
            transports = listOf(
                TransportInfo(type = TransportType.AMQP, config = TransportConfig.AmqpConfig(host = "r", port = 5672)),
            ),
        )

        val response = controller.register(request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("error", response.body?.status)
        verify(nodeRegistry, never()).register(any())
    }

    @Test
    fun `ONLINE with HTTP config missing host is rejected`() {
        val request = NodeRegistrationRequest(
            nodeId = "n1",
            transports = listOf(httpTransport(host = null)),
        )

        val response = controller.register(request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        verify(nodeRegistry, never()).register(any())
    }

    @Test
    fun `ONLINE with HTTP config missing port is rejected`() {
        val request = NodeRegistrationRequest(
            nodeId = "n1",
            transports = listOf(httpTransport(port = null)),
        )

        val response = controller.register(request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `ONLINE with HTTP config blank host is rejected`() {
        val request = NodeRegistrationRequest(
            nodeId = "n1",
            transports = listOf(httpTransport(host = "  ")),
        )

        val response = controller.register(request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `ONLINE with HTTP config zero port is rejected`() {
        val request = NodeRegistrationRequest(
            nodeId = "n1",
            transports = listOf(httpTransport(port = 0)),
        )

        val response = controller.register(request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `valid ONLINE registers the node and returns registered`() {
        val request = NodeRegistrationRequest(
            nodeId = "n1",
            transports = listOf(httpTransport()),
        )

        val response = controller.register(request)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("registered", response.body?.status)
        assertEquals("n1", response.body?.nodeId)
        val captor = argumentCaptor<NodeInfo>()
        verify(nodeRegistry).register(captor.capture())
        assertEquals("n1", captor.firstValue.nodeId)
        assertNotNull(captor.firstValue.transports)
    }

    @Test
    fun `default type is ONLINE`() {
        // Request without explicit type field - the controller must treat it as ONLINE.
        val request = NodeRegistrationRequest(
            nodeId = "n1",
            type = null,
            transports = listOf(httpTransport()),
        )

        val response = controller.register(request)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("registered", response.body?.status)
    }

    @Test
    fun `OFFLINE with remaining transports returns transport_removed`() {
        whenever(transportManager.handleTransportOffline("n1", TransportType.HTTP)).thenReturn(false)

        val response = controller.register(
            NodeRegistrationRequest(nodeId = "n1", type = NodeEventType.OFFLINE),
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("transport_removed", response.body?.status)
    }

    @Test
    fun `OFFLINE that removes the last transport returns deregistered`() {
        whenever(transportManager.handleTransportOffline("n1", TransportType.HTTP)).thenReturn(true)

        val response = controller.register(
            NodeRegistrationRequest(nodeId = "n1", type = NodeEventType.OFFLINE),
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("deregistered", response.body?.status)
    }

    @Test
    fun `unknown event type is rejected`() {
        val response = controller.register(
            NodeRegistrationRequest(nodeId = "n1", type = NodeEventType.INFO),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        verify(nodeRegistry, never()).register(any())
    }
}
