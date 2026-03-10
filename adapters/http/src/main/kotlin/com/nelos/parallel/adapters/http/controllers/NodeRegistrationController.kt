package com.nelos.parallel.adapters.http.controllers

import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.NodeTransportManager
import com.nelos.parallel.commons.adapter.enums.NodeEventType
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.request.NodeRegistrationRequest
import com.nelos.parallel.commons.adapter.vo.response.NodeRegistrationResponse
import com.nelos.parallel.commons.adapter.vo.response.TransportConfig
import com.nelos.parallel.commons.adapter.vo.response.TransportInfo
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.InetAddress
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
    fun register(
        @RequestBody request: NodeRegistrationRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<NodeRegistrationResponse> {
        if (request.nodeId.isBlank()) {
            return ResponseEntity.badRequest().body(
                NodeRegistrationResponse(status = "error", nodeId = request.nodeId)
            )
        }

        return when (request.type ?: NodeEventType.ONLINE) {
            NodeEventType.ONLINE -> handleOnline(request, httpRequest)
            NodeEventType.OFFLINE -> handleOffline(request)
            else -> ResponseEntity.badRequest().body(
                NodeRegistrationResponse(status = "error", nodeId = request.nodeId)
            )
        }
    }

    private fun handleOnline(
        request: NodeRegistrationRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<NodeRegistrationResponse> {
        val httpConfig = request.transports
            ?.firstOrNull { it.type == TransportType.HTTP }
            ?.config as? TransportConfig.HttpConfig

        val host = httpConfig?.host
        if (host.isNullOrBlank()) {
            LOG.warn("Node {} online event missing valid HTTP host", request.nodeId)
            return ResponseEntity.badRequest().body(
                NodeRegistrationResponse(status = "error", nodeId = request.nodeId)
            )
        }

        val port = httpConfig?.port
        if (port == null || port <= 0) {
            LOG.warn("Node {} online event missing valid HTTP port", request.nodeId)
            return ResponseEntity.badRequest().body(
                NodeRegistrationResponse(status = "error", nodeId = request.nodeId)
            )
        }

        val nodeInfo = NodeInfo(
            nodeId = request.nodeId,
            capabilities = request.capabilities ?: emptyMap(),
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

    private fun normalizeHost(addr: String): String {
        val inet = try { InetAddress.getByName(addr) } catch (_: Exception) { return addr }
        if (inet.isLoopbackAddress) return "127.0.0.1"
        return addr
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(NodeRegistrationController::class.java)
    }
}
