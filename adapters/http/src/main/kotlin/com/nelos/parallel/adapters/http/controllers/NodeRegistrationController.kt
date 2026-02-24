package com.nelos.parallel.adapters.http.controllers

import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.NodeRegistrationRequest
import com.nelos.parallel.commons.adapter.vo.NodeRegistrationResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.InetAddress
import java.time.Instant
import java.util.*

/**
 * REST controller for handling node registration and deregistration requests.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@RestController("prl.nodeRegistrationController")
class NodeRegistrationController @Autowired constructor(
    private val nodeRegistry: NodeRegistry
) {

    @PostMapping("/api/register")
    fun register(
        @RequestBody request: NodeRegistrationRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<NodeRegistrationResponse> {
        val host = request.host ?: normalizeHost(httpRequest.remoteAddr)

        return when (request.type ?: NodeRegistrationRequest.DEFAULT_TYPE) {
            NodeRegistrationRequest.TYPE_REGISTER -> handleRegister(request, host)
            NodeRegistrationRequest.TYPE_DEREGISTER -> handleDeregister(request)
            else -> ResponseEntity.badRequest().body(
                NodeRegistrationResponse(
                    status = "error",
                    nodeId = request.nodeId
                )
            )
        }
    }

    private fun handleRegister(
        request: NodeRegistrationRequest,
        host: String
    ): ResponseEntity<NodeRegistrationResponse> {
        val orchestratorToken = UUID.randomUUID().toString()

        val nodeInfo = NodeInfo(
            nodeId = request.nodeId,
            transport = request.transport ?: TransportType.HTTP,
            host = host,
            port = request.port,
            authToken = request.authToken,
            capabilities = request.capabilities ?: emptyMap(),
            registeredAt = Instant.now()
        )

        nodeRegistry.register(nodeInfo)
        LOG.info("Node registered via HTTP: {} ({}:{})", request.nodeId, host, request.port)

        return ResponseEntity.ok(
            NodeRegistrationResponse(
                status = "registered",
                nodeId = request.nodeId,
                orchestratorAuthToken = orchestratorToken,
                timestamp = Instant.now()
            )
        )
    }

    private fun handleDeregister(request: NodeRegistrationRequest): ResponseEntity<NodeRegistrationResponse> {
        val removed = nodeRegistry.deregister(request.nodeId)

        if (removed) {
            LOG.info("Node deregistered: {}", request.nodeId)
        } else {
            LOG.warn("Attempted to deregister unknown node: {}", request.nodeId)
        }

        return ResponseEntity.ok(
            NodeRegistrationResponse(
                status = if (removed) "deregistered" else "not_found",
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
