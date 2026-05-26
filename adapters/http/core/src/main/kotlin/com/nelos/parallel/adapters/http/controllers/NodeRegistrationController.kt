package com.nelos.parallel.adapters.http.controllers

import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.NodeTransportManager
import com.nelos.parallel.commons.adapter.enums.NodeEventType
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.request.NodeRegistrationRequest
import com.nelos.parallel.commons.adapter.vo.response.NodeRegistrationResponse
import com.nelos.parallel.commons.adapter.vo.response.TransportConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * REST controller for handling node registration and deregistration requests.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@RestController("prl.nodeRegistrationController")
class NodeRegistrationController @Autowired constructor(
    private val nodeRegistry: NodeRegistry,
    private val transportManager: NodeTransportManager,
) {

    @PostMapping("/api/register")
    fun register(@RequestBody request: NodeRegistrationRequest): ResponseEntity<NodeRegistrationResponse> {
        if (request.nodeId.isBlank()) {
            return ResponseEntity.badRequest().body(
                NodeRegistrationResponse(status = "error", nodeId = request.nodeId)
            )
        }
        return when (request.type ?: NodeEventType.ONLINE) {
            NodeEventType.ONLINE -> handleOnline(request)
            NodeEventType.OFFLINE -> handleOffline(request)
            else -> ResponseEntity.badRequest().body(
                NodeRegistrationResponse(status = "error", nodeId = request.nodeId)
            )
        }
    }

    private fun handleOnline(request: NodeRegistrationRequest): ResponseEntity<NodeRegistrationResponse> {
        // /api/register is HTTP-only by design. AMQP nodes are discovered via
        // RabbitNodeAdapter.discoverNodes, not this controller.
        val httpConfig = request.transports
            ?.firstOrNull { it.type == TransportType.HTTP }
            ?.config as? TransportConfig.HttpConfig
            ?: return badRequest(request.nodeId, "HTTP registration requires an HTTP transport config")

        val host = httpConfig.host?.takeIf { it.isNotBlank() }
            ?: return badRequest(request.nodeId, "missing valid HTTP host")
        val port = httpConfig.port?.takeIf { it > 0 }
            ?: return badRequest(request.nodeId, "missing valid HTTP port")

        val nodeInfo = NodeInfo(
            nodeId = request.nodeId,
            capabilities = request.capabilities,
            transports = request.transports,
            resourceProviders = request.resourceProviders,
            registeredAt = Instant.now()
        )

        nodeRegistry.register(nodeInfo)
        LOG.info("Node online via HTTP: {} ({}:{})", request.nodeId, host, port)

        return ResponseEntity.ok(
            NodeRegistrationResponse(
                status = "registered",
                nodeId = request.nodeId,
                timestamp = Instant.now()
            )
        )
    }

    private fun handleOffline(request: NodeRegistrationRequest): ResponseEntity<NodeRegistrationResponse> {
        val fullyRemoved = transportManager.handleTransportOffline(request.nodeId, TransportType.HTTP)
        LOG.info("Node {} HTTP offline (fullyRemoved={})", request.nodeId, fullyRemoved)

        return ResponseEntity.ok(
            NodeRegistrationResponse(
                status = if (fullyRemoved) "deregistered" else "transport_removed",
                nodeId = request.nodeId,
                timestamp = Instant.now()
            )
        )
    }

    private fun badRequest(nodeId: String, reason: String): ResponseEntity<NodeRegistrationResponse> {
        LOG.warn("Node {} online event rejected: {}", nodeId, reason)
        return ResponseEntity.badRequest().body(NodeRegistrationResponse(status = "error", nodeId = nodeId))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(NodeRegistrationController::class.java)
    }
}
