package com.nelos.parallel.pipeline.core.service.impl

import com.nelos.parallel.commons.adapter.NodeAdapterRegistry
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.NodeTransportManager
import com.nelos.parallel.commons.adapter.enums.AdapterStatus
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.pipeline.commons.service.RunnerSelector
import com.nelos.parallel.pipeline.commons.service.SelectedRunner
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.runnerSelector")
class RunnerSelectorImpl(
    private val nodeRegistry: NodeRegistry,
    private val transportManager: NodeTransportManager,
    private val adapterRegistry: NodeAdapterRegistry,
    private val submissionLogger: SubmissionLogger,
) : RunnerSelector {

    /**
     * Three-tier lookup:
     *   1. Cached registry snapshot (fast).
     *   2. Cache-invalidate + re-read (catches cluster-mate writes).
     *   3. Broadcast an AMQP `statusRequest` and try once more - covers the
     *      "engine reachable over Rabbit but never pushed itself into the
     *      registry" case (only the HTTP adapter calls /api/register).
     */
    override fun selectRunner(submissionId: Long): SelectedRunner? = selectInternal(submissionId, null)

    override fun selectRunner(submissionId: Long, transport: TransportType): SelectedRunner? =
        selectInternal(submissionId, transport)

    private fun selectInternal(submissionId: Long, transport: TransportType?): SelectedRunner? {
        selectOnce(submissionId, transport)?.let { return it }
        nodeRegistry.invalidateCache()
        selectOnce(submissionId, transport)?.let { return it }
        // AMQP discovery applies regardless of the requested transport: an
        // engine reachable over AMQP only registers itself by responding to
        // the broadcast. The result is still filtered by `transport` below.
        val discovered = transportManager.discoverAndRefresh(TransportType.AMQP)
        if (discovered.isEmpty()) return null
        bestEffortLog(
            submissionId,
            "[parallel] AMQP discovery picked up ${discovered.size} node(s); retrying dispatch..."
        )
        return selectOnce(submissionId, transport)
    }

    /**
     * Walk the registered adapters in preference order (configured in
     * [NodeAdapterRegistry]). For each, hand the list of nodes that own its
     * transport to the adapter's own [com.nelos.parallel.commons.adapter.NodeAdapter.pickRunnerNode]
     * and let the adapter decide which (if any) is usable - broker-routed
     * transports trivially return the first; point-to-point transports probe
     * each candidate.
     */
    private fun selectOnce(submissionId: Long, transport: TransportType?): SelectedRunner? {
        val nodes = nodeRegistry.findAll()
        if (nodes.isEmpty()) return null
        val nodesByTransport = bucketByTransport(nodes)

        for (adapter in adapterRegistry.adaptersInPreferenceOrder) {
            if (transport != null && adapter.transportType != transport) continue
            val candidates = nodesByTransport[adapter.transportType].orEmpty()
            if (candidates.isEmpty()) continue
            val pick = adapter.pickRunnerNode(candidates)
            reportDead(submissionId, adapter.transportType, pick.deadNodes)
            pick.live?.let { return SelectedRunner(it, adapter, adapter.transportType) }
        }
        return null
    }

    /** Bucket nodes by every running transport they own. */
    private fun bucketByTransport(nodes: List<NodeInfo>): Map<TransportType, List<NodeInfo>> {
        val result = mutableMapOf<TransportType, MutableList<NodeInfo>>()
        for (node in nodes) {
            node.transports
                ?.filter { it.config != null && it.status !in DEAD_STATUSES }
                ?.forEach { t -> result.getOrPut(t.type) { mutableListOf() }.add(node) }
        }
        return result
    }

    /** Strip [transport] from each node the adapter reported as unreachable. */
    private fun reportDead(submissionId: Long, transport: TransportType, deadNodeIds: List<String>) {
        if (deadNodeIds.isEmpty()) return
        deadNodeIds.forEach { transportManager.handleHealthCheckFailure(it, transport) }
        LOG.info("Lazy validation stripped {} transport from {} unresponsive node(s)", transport, deadNodeIds.size)
        submissionLogger.appendOne(
            submissionId,
            "[parallel] Removed $transport from ${deadNodeIds.size} unresponsive node(s)",
        )
    }

    private fun bestEffortLog(submissionId: Long, line: String) {
        runCatching { submissionLogger.appendOne(submissionId, line) }.onFailure {
            LOG.warn("Failed to append log line for submission {}: {}", submissionId, it.message)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RunnerSelectorImpl::class.java)
        private val DEAD_STATUSES = setOf(AdapterStatus.STOPPED, AdapterStatus.FAILED)
    }
}
