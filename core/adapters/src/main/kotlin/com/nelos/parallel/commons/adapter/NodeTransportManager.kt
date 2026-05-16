package com.nelos.parallel.commons.adapter

import com.nelos.parallel.commons.adapter.enums.AdapterStatus
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Manages transport-aware node deregistration and health check failures.
 *
 * Instead of removing an entire node when a single transport goes offline or fails a health check,
 * this service removes only the affected transport and attempts to refresh the node via remaining transports.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.nodeTransportManager")
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
            // Race guard: if the node re-registered itself during our network
            // probe loop (a fresh ONLINE event for the same nodeId), the
            // current registry state already supersedes `remaining`. Wiping it
            // now would destroy the live re-registration.
            if (registeredSince(nodeId, remaining.registeredAt)) {
                LOG.info("Aborting offline handler for {} - node re-registered during probe", nodeId)
                return false
            }
            nodeRegistry.deregister(nodeId)
            LOG.info("Node {} removed (no running transports responded)", nodeId)
            return true
        }

        for (type in nonResponding) {
            // Same race guard before stripping transports.
            if (registeredSince(nodeId, remaining.registeredAt)) {
                LOG.info("Aborting transport cleanup for {} - node re-registered during probe", nodeId)
                return false
            }
            nodeRegistry.removeTransport(nodeId, type)
        }
        return false
    }

    /** True if a (presumed-newer) registration of [nodeId] is present in the
     *  registry with a `registeredAt` strictly newer than [sinceTimestamp]. */
    private fun registeredSince(nodeId: String, sinceTimestamp: java.time.Instant): Boolean {
        val current = nodeRegistry.findById(nodeId) ?: return false
        return current.registeredAt.isAfter(sinceTimestamp)
    }

    /**
     * Triggers an on-demand discovery on the given transport (currently meaningful
     * only for AMQP - HTTP returns `emptyList()`). Each responding node is merged
     * into the registry; previously-registered nodes of the same transport type
     * that didn't reply within the discovery timeout have THAT transport stripped
     * (other transports on the same node, if any, are kept). Pipeline submit calls
     * this when no live node is found in the local snapshot.
     *
     * @return the freshly discovered nodes (those that replied)
     */
    fun discoverAndRefresh(transport: TransportType): List<NodeInfo> {
        val adapter = adapterRegistry.findAdapter(transport) ?: return emptyList()
        val discovered = adapter.discoverNodes()
        val responding = discovered.map { it.nodeId }.toSet()
        LOG.info("{} discovery: {} node(s) replied", transport, discovered.size)

        nodeRegistry.findByTransport(transport)
            .filter { it.nodeId !in responding }
            .forEach { stale ->
                handleHealthCheckFailure(stale.nodeId, transport)
                LOG.info("{} node did not respond to discovery: {}", transport, stale.nodeId)
            }

        discovered.forEach { nodeRegistry.register(it) }
        return discovered
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
