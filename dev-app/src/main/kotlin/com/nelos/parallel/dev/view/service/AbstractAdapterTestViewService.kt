package com.nelos.parallel.dev.view.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.NodeTransportManager
import com.nelos.parallel.commons.adapter.enums.SourceType
import com.nelos.parallel.commons.adapter.enums.TestMode
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.listener.TaskResultListenerRegistry
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.request.ConfigUpdateRequest
import com.nelos.parallel.commons.adapter.vo.request.SourceDescriptor
import com.nelos.parallel.commons.adapter.vo.request.TaskSubmission
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.dev.view.vo.*
import com.nelos.parallel.dev.vo.AdapterRequest
import com.nelos.parallel.dev.vo.ConfigRequest
import com.nelos.parallel.dev.vo.JobQueryRequest
import com.nelos.parallel.dev.vo.ResourceProviderRequest
import com.nelos.parallel.dev.vo.TaskSubmitRequest
import org.slf4j.Logger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class for adapter test view services — provides common operations
 * shared between HTTP and RabbitMQ adapter testing panels.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
abstract class AbstractAdapterTestViewService(
    protected val nodeRegistry: NodeRegistry,
    protected val objectMapper: ObjectMapper,
    protected val listenerRegistry: TaskResultListenerRegistry,
    protected val transportManager: NodeTransportManager,
) {

    private val resultCache = ConcurrentHashMap<String, TaskResult>()

    protected abstract val transportType: TransportType

    protected abstract val log: Logger

    protected abstract val adapter: NodeAdapter

    fun getNodes(): List<NodeView> =
        nodeRegistry.findByTransport(transportType).map { it.toView() }

    fun refreshAndPruneNodes(): List<NodeView> {
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
                capabilities = status.capabilities ?: emptyMap(),
                queue = status.effectiveQueue(),
                transports = status.transports,
                resourceProviders = status.resourceProviders,
            )
        }

    fun queueStatus(nodeId: String): QueueStatusView =
        withNode(nodeId) { node ->
            val qs = adapter.queueStatus(node)
            toQueueStatusView(nodeId, qs)
        }

    fun submitTask(data: TaskSubmitRequest): TaskSubmitResultView {
        val nodeId = data.nodeId
            ?: throw IllegalArgumentException("nodeId is required")
        return withNode(nodeId) { node ->
            val jobId = data.jobId ?: UUID.randomUUID().toString()
            listenerRegistry.register(jobId) { result ->
                resultCache[jobId] = result
            }
            try {
                val task = TaskSubmission(
                    jobId = jobId,
                    testId = data.testId ?: "",
                    solutionSourceType = data.solutionSourceType?.let { SourceType.fromValue(it) },
                    solutionSource = buildSourceDescriptor(
                        data.solutionSourceType, data.solutionUrl, data.solutionBranch,
                        data.solutionToken, data.solutionPath
                    ),
                    testSourceType = data.testSourceType?.let { SourceType.fromValue(it) },
                    testSource = buildSourceDescriptor(
                        data.testSourceType, data.testUrl, data.testBranch,
                        data.testToken, data.testPath
                    ),
                    mode = TestMode.fromValue(data.mode ?: "correctness"),
                    threads = data.threads,
                    numaNode = data.numaNode,
                    callbackUrl = resolveCallbackUrl(),
                    memoryLimitMb = data.memoryLimitMb,
                )
                val response = adapter.submitTask(node, task)
                TaskSubmitResultView(
                    jobId = response.jobId,
                    status = response.status,
                    position = response.position,
                    nodeId = response.nodeId,
                    mode = response.mode,
                    solution = response.solution,
                    memoryLimitMb = response.memoryLimitMb,
                    timestamp = response.timestamp,
                )
            } catch (e: Exception) {
                listenerRegistry.unregister(jobId)
                throw e
            }
        }
    }

    private fun buildSourceDescriptor(
        type: String?,
        url: String?,
        branch: String?,
        token: String?,
        path: String?
    ): SourceDescriptor? =
        when (type?.lowercase()) {
            "git" -> SourceDescriptor.GitSource(
                url = url ?: error("url is required for git source"),
                branch = branch,
                token = token,
            )
            "local" -> SourceDescriptor.LocalSource(
                path = path ?: error("path is required for local source"),
            )
            null, "" -> null
            else -> error("Unknown source type: $type")
        }

    fun queryJobStatus(data: JobQueryRequest): JobStatusView {
        val nodeId = data.nodeId ?: throw IllegalArgumentException("nodeId is required")
        val jobId = data.jobId ?: throw IllegalArgumentException("jobId is required")
        return withNode(nodeId) { node ->
            val result = adapter.queryJobStatus(node, jobId)
            JobStatusView(
                jobId = result.jobId,
                nodeId = result.nodeId,
                status = result.status,
                error = result.error,
                durationMs = result.durationMs,
                mode = result.mode,
                solution = result.solution,
                buildOutput = result.buildOutput,
                correctness = result.correctness,
                performance = result.performance,
                buildInfo = result.buildInfo,
                lane = result.lane,
                position = result.position,
                timestamp = result.timestamp,
            )
        }
    }

    fun cancelJob(data: JobQueryRequest): CancelJobView {
        val nodeId = data.nodeId ?: throw IllegalArgumentException("nodeId is required")
        val jobId = data.jobId ?: throw IllegalArgumentException("jobId is required")
        return withNode(nodeId) { node ->
            val result = adapter.cancelJob(node, jobId)
            CancelJobView(result.jobId, result.status, result.error)
        }
    }

    fun updateConfig(data: ConfigRequest): QueueStatusView {
        val nodeId = data.nodeId ?: throw IllegalArgumentException("nodeId is required")
        return withNode(nodeId) { node ->
            val request = ConfigUpdateRequest(
                maxCorrectnessWorkers = data.maxCorrectnessWorkers,
                jobRetentionSeconds = data.jobRetentionSeconds,
                defaultMemoryLimitMb = data.defaultMemoryLimitMb,
            )
            val qs = adapter.updateConfig(node, request)
            toQueueStatusView(nodeId, qs)
        }
    }

    fun listAdapters(nodeId: String): List<AdapterView> =
        withNode(nodeId) { node ->
            adapter.listAdapters(node).map { AdapterView(it.name, it.status, it.dllPath, it.type, it.config) }
        }

    fun listAvailableAdapters(nodeId: String): List<AdapterView> =
        withNode(nodeId) { node ->
            adapter.listAvailableAdapters(node).map { AdapterView(it.name, it.status, it.dllPath, it.type, it.config) }
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

    fun listResourceProviders(nodeId: String): List<ResourceProviderView> =
        withNode(nodeId) { node ->
            adapter.listResourceProviders(node).map { ResourceProviderView(it.name, it.status, it.dllPath, it.config) }
        }

    fun listAvailableResourceProviders(nodeId: String): List<ResourceProviderView> =
        withNode(nodeId) { node ->
            adapter.listAvailableResourceProviders(node).map { ResourceProviderView(it.name, it.status, it.dllPath, it.config) }
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

    fun pollTaskResult(jobId: String): JobStatusView? {
        val result = resultCache.remove(jobId) ?: return null
        return JobStatusView(
            jobId = result.jobId ?: jobId,
            nodeId = result.nodeId,
            status = result.status ?: "unknown",
            error = result.error,
            durationMs = result.durationMs,
            mode = result.mode,
            solution = result.solution,
            buildOutput = result.buildOutput,
            correctness = result.correctness,
            performance = result.performance,
            buildInfo = result.buildInfo,
            lane = result.lane,
            position = result.position,
            timestamp = result.timestamp,
        )
    }

    fun removeNode(nodeId: String): RemoveNodeView {
        val removed = nodeRegistry.deregister(nodeId)
        return RemoveNodeView(nodeId, removed)
    }

    private fun toQueueStatusView(nodeId: String, qs: com.nelos.parallel.commons.adapter.vo.response.QueueStatus) =
        QueueStatusView(
            nodeId = nodeId,
            correctnessQueue = qs.effectiveCorrectnessQueue()?.let {
                QueueInfoView(it.queued, it.running, it.totalWorkers)
            },
            performanceQueue = qs.effectivePerformanceQueue()?.let {
                QueueInfoView(it.queued, it.running, it.totalWorkers)
            },
            status = qs.status,
            maxCorrectnessWorkers = qs.maxCorrectnessWorkers,
            maxOmpThreads = qs.maxOmpThreads,
            currentPerfJob = qs.currentPerfJob,
            jobRetentionSeconds = qs.jobRetentionSeconds,
            defaultMemoryLimitMb = qs.defaultMemoryLimitMb,
        )

    protected open fun resolveCallbackUrl(): String? = null

    protected fun <T> withNode(nodeId: String, action: (NodeInfo) -> T): T {
        val node = nodeRegistry.findById(nodeId)
            ?: throw IllegalArgumentException("Node not found: $nodeId")
        return action(node)
    }

    protected fun NodeInfo.toView(): NodeView = NodeView(
        nodeId = nodeId,
        capabilities = capabilities ?: emptyMap(),
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

    companion object {
        const val CALLBACK_URL = "/api/callback/result"
    }
}
