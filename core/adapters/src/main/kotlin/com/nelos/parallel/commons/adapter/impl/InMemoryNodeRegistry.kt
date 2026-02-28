package com.nelos.parallel.commons.adapter.impl

import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.findHttpConfig
import com.nelos.parallel.commons.adapter.vo.findTransport
import com.nelos.parallel.commons.adapter.vo.response.TransportConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory implementation of [NodeRegistry].
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.inMemoryNodeRegistry")
class InMemoryNodeRegistry : NodeRegistry {

    private val nodes = ConcurrentHashMap<String, NodeInfo>()

    override fun register(node: NodeInfo): NodeInfo {
        synchronized(nodes) {
            evictByEndpoint(node)
            val existing = nodes[node.nodeId]
            if (existing != null && node.registeredAt.isBefore(existing.registeredAt)) {
                LOG.debug("Ignoring outdated registration for node {} (existing={}; incoming={})",
                    node.nodeId, existing.registeredAt, node.registeredAt)
                return existing
            }
            nodes[node.nodeId] = node
            if (existing == null) {
                LOG.info("Node registered: {} (transports={})", node.nodeId, formatTransports(node))
            } else {
                LOG.info("Node re-registered: {} (transports={})", node.nodeId, formatTransports(node))
            }
            return node
        }
    }

    private fun evictByEndpoint(node: NodeInfo) {
        val httpConfig = node.findHttpConfig() ?: return
        val port = httpConfig.port ?: return
        if (port <= 0) return
        val host = httpConfig.host ?: return

        val stale = nodes.values.firstOrNull { existing ->
            if (existing.nodeId == node.nodeId) return@firstOrNull false
            val existingHttp = existing.findHttpConfig() ?: return@firstOrNull false
            existingHttp.host == host && existingHttp.port == port
        } ?: return

        nodes.remove(stale.nodeId)
        LOG.info("Evicted stale node {} (same endpoint {}:{})", stale.nodeId, host, port)
    }

    override fun deregister(nodeId: String): Boolean {
        val removed = nodes.remove(nodeId) != null
        if (removed) {
            LOG.info("Node deregistered: {}", nodeId)
        } else {
            LOG.debug("Node not found for deregistration: {}", nodeId)
        }
        return removed
    }

    override fun findById(nodeId: String): NodeInfo? =
        nodes[nodeId]

    override fun findAll(): List<NodeInfo> =
        nodes.values.toList()

    override fun findByTransport(transport: TransportType): List<NodeInfo> =
        nodes.values.filter { it.findTransport(transport) != null }

    override fun removeTransport(nodeId: String, transportType: TransportType): NodeInfo? {
        synchronized(nodes) {
            val node = nodes[nodeId] ?: return null
            val updatedTransports = node.transports?.filter { it.type != transportType }
            if (updatedTransports.isNullOrEmpty()) {
                nodes.remove(nodeId)
                LOG.info("Removed node {} (no transports after removing {})", nodeId, transportType)
                return null
            }
            val updated = NodeInfo(
                nodeId = node.nodeId,
                capabilities = node.capabilities,
                transports = updatedTransports,
                resourceProviders = node.resourceProviders,
                registeredAt = node.registeredAt
            )
            nodes[nodeId] = updated
            LOG.info("Removed {} transport from node {} ({} remaining)", transportType, nodeId, updatedTransports.size)
            return updated
        }
    }

    override fun updateNode(node: NodeInfo): NodeInfo {
        nodes[node.nodeId] = node
        return node
    }

    private fun formatTransports(node: NodeInfo): String =
        node.transports?.joinToString { t ->
            val endpoint = (t.config as? TransportConfig.HttpConfig)?.let { c ->
                " ${c.host ?: "?"}:${c.port ?: "?"}"
            } ?: ""
            "${t.type.toValue()}$endpoint"
        } ?: "none"

    companion object {
        private val LOG = LoggerFactory.getLogger(InMemoryNodeRegistry::class.java)
    }
}
