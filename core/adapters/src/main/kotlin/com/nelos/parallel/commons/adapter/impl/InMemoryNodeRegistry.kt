package com.nelos.parallel.commons.adapter.impl

import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
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

    override fun register(node: NodeInfo): NodeInfo =
        node.also {
            val isNew = nodes.put(it.nodeId, it) == null
            if (isNew) {
                LOG.info("Node registered: {} (transport={}, host={}:{})", it.nodeId, it.transport, it.host, it.port)
            } else {
                LOG.debug("Node re-registered: {}", it.nodeId)
            }
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
        nodes.values.filter { it.transport == transport }

    companion object {
        private val LOG = LoggerFactory.getLogger(InMemoryNodeRegistry::class.java)
    }
}
