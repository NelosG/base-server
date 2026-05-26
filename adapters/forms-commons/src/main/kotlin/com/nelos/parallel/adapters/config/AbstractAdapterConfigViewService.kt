package com.nelos.parallel.adapters.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.nelos.parallel.adapters.config.vo.AdapterActionView
import com.nelos.parallel.adapters.config.vo.AdapterRequest
import com.nelos.parallel.adapters.config.vo.AdapterView
import com.nelos.parallel.adapters.config.vo.ConfigRequest
import com.nelos.parallel.adapters.config.vo.HealthCheckView
import com.nelos.parallel.adapters.config.vo.NodeStatusView
import com.nelos.parallel.adapters.config.vo.NodeView
import com.nelos.parallel.adapters.config.vo.QueueStatusView
import com.nelos.parallel.adapters.config.vo.RemoveNodeView
import com.nelos.parallel.adapters.config.vo.ResourceProviderActionView
import com.nelos.parallel.adapters.config.vo.ResourceProviderRequest
import com.nelos.parallel.adapters.config.vo.ResourceProviderView
import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.NodeTransportManager
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.request.ConfigUpdateRequest
import org.slf4j.Logger

/**
 * Base for the admin-only adapter-configuration ViewServices (HTTP and AMQP).
 * Holds every operation that doesn't require knowing which transport we're
 * talking to - registered-node listing, plugin load/unload, dynamic engine
 * config edits. Submission/job-status methods that lived on the old "test"
 * panel are gone; submissions go through the regular pipeline, not this
 * config UI.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
abstract class AbstractAdapterConfigViewService(
    protected val nodeRegistry: NodeRegistry,
    protected val objectMapper: ObjectMapper,
    protected val transportManager: NodeTransportManager,
) {

    protected abstract val transportType: TransportType
    protected abstract val log: Logger
    protected abstract val adapter: NodeAdapter

    // ---- node-level listing / lifecycle --------------------------------

    fun getNodes(): List<NodeView> =
        nodeRegistry.findByTransport(transportType).map { it.toView() }

    open fun refreshAndPruneNodes(): List<NodeView> {
        nodeRegistry.findByTransport(transportType)
            .filterNot { runCatching { adapter.healthCheck(it) }.getOrDefault(false) }
            .forEach { node ->
                transportManager.handleHealthCheckFailure(node.nodeId, transportType)
                log.info("Health check failed for {} transport on node: {}", transportType.name, node.nodeId)
            }
        return getNodes()
    }

    fun healthCheck(nodeId: String): HealthCheckView {
        val node = nodeRegistry.findById(nodeId)
            ?: throw IllegalArgumentException("Node not found: $nodeId")
        return HealthCheckView(nodeId, adapter.healthCheck(node))
    }

    fun pollNodeStatus(nodeId: String): NodeStatusView =
        withNode(nodeId) { node ->
            val status = adapter.queryNodeStatus(node)
            NodeStatusView(
                nodeId = status.nodeId,
                capabilities = status.capabilities,
                queue = status.currentLoad,
                engineConfig = status.engineConfig,
                transports = status.transports,
                resourceProviders = status.resourceProviders,
            )
        }

    fun queueStatus(nodeId: String): QueueStatusView =
        withNode(nodeId) { node ->
            val qs = adapter.queueStatus(node)
            val engineConfig = runCatching { adapter.queryNodeStatus(node).engineConfig }.getOrNull()
            QueueStatusView(
                nodeId = nodeId,
                status = qs.status,
                queueSize = qs.queueSize,
                activeJobs = qs.activeJobs,
                perfPhaseRunning = qs.perfPhaseRunning,
                perfPhasePending = qs.perfPhasePending,
                maxCorrectnessWorkers = qs.maxCorrectnessWorkers,
                jobs = qs.jobs,
                engineConfig = engineConfig,
            )
        }

    fun removeNode(nodeId: String): RemoveNodeView =
        RemoveNodeView(nodeId, nodeRegistry.deregister(nodeId))

    // ---- engine config edits -------------------------------------------

    fun updateConfig(data: ConfigRequest): QueueStatusView {
        val nodeId = data.nodeId ?: throw IllegalArgumentException("nodeId is required")
        return withNode(nodeId) { node ->
            val request = ConfigUpdateRequest(
                maxCorrectnessWorkers = data.maxCorrectnessWorkers,
                jobRetentionSeconds = data.jobRetentionSeconds,
                defaultMemoryLimitMb = data.defaultMemoryLimitMb,
                defaultThreads = data.defaultThreads,
                defaultWallTimeSec = data.defaultWallTimeSec,
                defaultCpuTimeSec = data.defaultCpuTimeSec,
                sandboxProcessMultiplier = data.sandboxProcessMultiplier,
            )
            adapter.updateConfig(node, request)
            queueStatus(nodeId)
        }
    }

    // ---- adapter plugins -----------------------------------------------

    fun listAdapters(nodeId: String): List<AdapterView> =
        withNode(nodeId) { node ->
            adapter.listAdapters(node).map { AdapterView(it.name, it.status, it.config) }
        }

    fun listAvailableAdapters(nodeId: String): List<AdapterView> =
        withNode(nodeId) { node ->
            adapter.listAvailableAdapters(node).map { AdapterView(it.name, it.status, it.config) }
        }

    fun loadAdapter(data: AdapterRequest): AdapterActionView {
        val nodeId = data.nodeId ?: throw IllegalArgumentException("nodeId is required")
        val adapterName = data.adapterName ?: throw IllegalArgumentException("adapterName is required")
        return withNode(nodeId) { node ->
            val config = parseConfig(data.config)
            val result = adapter.loadAdapter(node, adapterName, config)
            AdapterActionView(result.adapter, result.status, result.error)
        }
    }

    fun unloadAdapter(data: AdapterRequest): AdapterActionView {
        val nodeId = data.nodeId ?: throw IllegalArgumentException("nodeId is required")
        val adapterName = data.adapterName ?: throw IllegalArgumentException("adapterName is required")
        return withNode(nodeId) { node ->
            val result = adapter.unloadAdapter(node, adapterName)
            AdapterActionView(result.adapter, result.status, result.error)
        }
    }

    // ---- resource providers --------------------------------------------

    fun listResourceProviders(nodeId: String): List<ResourceProviderView> =
        withNode(nodeId) { node ->
            adapter.listResourceProviders(node).map { ResourceProviderView(it.name, it.status, it.config) }
        }

    fun listAvailableResourceProviders(nodeId: String): List<ResourceProviderView> =
        withNode(nodeId) { node ->
            adapter.listAvailableResourceProviders(node).map { ResourceProviderView(it.name, it.status, it.config) }
        }

    fun loadResourceProvider(data: ResourceProviderRequest): ResourceProviderActionView {
        val nodeId = data.nodeId ?: throw IllegalArgumentException("nodeId is required")
        val providerName = data.providerName ?: throw IllegalArgumentException("providerName is required")
        return withNode(nodeId) { node ->
            val config = parseConfig(data.config)
            val result = adapter.loadResourceProvider(node, providerName, config)
            ResourceProviderActionView(result.provider, result.status, result.error)
        }
    }

    fun unloadResourceProvider(data: ResourceProviderRequest): ResourceProviderActionView {
        val nodeId = data.nodeId ?: throw IllegalArgumentException("nodeId is required")
        val providerName = data.providerName ?: throw IllegalArgumentException("providerName is required")
        return withNode(nodeId) { node ->
            val result = adapter.unloadResourceProvider(node, providerName)
            ResourceProviderActionView(result.provider, result.status, result.error)
        }
    }

    // ---- helpers --------------------------------------------------------

    protected fun <T> withNode(nodeId: String, action: (NodeInfo) -> T): T {
        val node = nodeRegistry.findById(nodeId)
            ?: throw IllegalArgumentException("Node not found: $nodeId")
        return action(node)
    }

    protected fun NodeInfo.toView(): NodeView = NodeView(
        nodeId = nodeId,
        capabilities = capabilities,
        transports = transports,
        resourceProviders = resourceProviders,
        registeredAt = registeredAt.toString(),
    )

    protected fun parseConfig(json: String?): ObjectNode? {
        if (json.isNullOrBlank() || json == "{}") return null
        return runCatching {
            objectMapper.readTree(json) as? ObjectNode
        }.getOrNull()
    }
}
