package com.nelos.parallel.commons.adapter

import com.nelos.parallel.commons.adapter.enums.AdapterStatus
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import org.slf4j.LoggerFactory

/**
 * Manages transport-aware node deregistration and health check failures.
 *
 * Instead of removing an entire node when a single transport goes offline or fails a health check,
 * this service removes only the affected transport and attempts to refresh the node via remaining transports.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class NodeTransportManager(
    private val nodeRegistry: NodeRegistry,
    private val adapterRegistry: NodeAdapterRegistry,
) {

    /**
     * Handles an OFFLINE event for a specific transport on a node.
     *
     * Removes the offline transport, then attempts to query the node via remaining running transports.
     * If at least one responds, the node info is refreshed (capabilities + resourceProviders updated,
     * transport configs preserved). Non-responding transports are also removed.
     * If none respond, the entire node is deregistered.
     *
     * @return `true` if the node was fully removed, `false` if it's still alive via other transports
     */
    fun handleTransportOffline(nodeId: String, offlineTransport: TransportType): Boolean {
        val remaining = nodeRegistry.removeTransport(nodeId, offlineTransport)
        if (remaining == null) {
            LOG.info("Node {} removed (no transports after {} offline)", nodeId, offlineTransport)
            return true
        }

        val candidates = remaining.transports?.filter {
            it.status == null || it.status == AdapterStatus.RUNNING
        } ?: emptyList()

        if (candidates.isEmpty()) {
            nodeRegistry.deregister(nodeId)
            LOG.info("Node {} removed (no running transports remain)", nodeId)
            return true
        }

        val nonResponding = mutableListOf<TransportType>()
        var refreshed = false

        for (ti in candidates) {
            val adapter = adapterRegistry.findAdapter(ti.type)
            if (adapter == null) {
                LOG.debug("No adapter for transport {} on node {}", ti.type, nodeId)
                continue
            }
            try {
                val status = adapter.queryNodeStatus(remaining)
                val updated = NodeInfo(
                    nodeId = remaining.nodeId,
                    capabilities = status.capabilities ?: remaining.capabilities,
                    transports = remaining.transports,
                    resourceProviders = status.resourceProviders ?: remaining.resourceProviders,
                    registeredAt = remaining.registeredAt
                )
                nodeRegistry.updateNode(updated)
                refreshed = true
                LOG.info("Refreshed node {} via {} after {} offline", nodeId, ti.type, offlineTransport)
                break
            } catch (e: Exception) {
                LOG.warn("Failed to query node {} via {}: {}", nodeId, ti.type, e.message)
                nonResponding.add(ti.type)
            }
        }

        if (!refreshed) {
            nodeRegistry.deregister(nodeId)
            LOG.info("Node {} removed (no running transports responded)", nodeId)
            return true
        }

        for (type in nonResponding) {
            nodeRegistry.removeTransport(nodeId, type)
        }
        return false
    }

    /**
     * Handles a health check failure for a specific transport on a node.
     *
     * Simply removes the failed transport. If no transports remain, the entire node is deregistered.
     *
     * @return `true` if the node was fully removed, `false` if other transports remain
     */
    fun handleHealthCheckFailure(nodeId: String, failedTransport: TransportType): Boolean {
        val remaining = nodeRegistry.removeTransport(nodeId, failedTransport)
        if (remaining != null) {
            LOG.info("Removed {} transport from node {} after health check failure", failedTransport, nodeId)
        } else {
            LOG.info("Node {} removed (no transports after {} health check failure)", nodeId, failedTransport)
        }
        return remaining == null
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(NodeTransportManager::class.java)
    }
}
