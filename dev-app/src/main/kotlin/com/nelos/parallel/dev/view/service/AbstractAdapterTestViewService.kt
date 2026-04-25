package com.nelos.parallel.dev.view.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.NodeTransportManager
import com.nelos.parallel.commons.adapter.enums.SourceType
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.listener.TaskResultListenerRegistry
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.request.ConfigUpdateRequest
import com.nelos.parallel.commons.adapter.vo.request.SourceDescriptor
import com.nelos.parallel.commons.adapter.vo.request.TaskSubmission
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.dev.view.vo.*
import com.nelos.parallel.dev.vo.*
import org.slf4j.Logger
import java.time.Duration
import java.util.*

/**
 * Base class for adapter test view services - provides common operations
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

    /**
     * Stores the latest [TaskResult] per jobId for the admin-test polling endpoint.
     * Caffeine bounds memory both by size (max 64 entries) and time (10 min after write):
     * if the admin closes the browser mid-test, the result is evicted automatically.
     * Concurrency-safe by design - populated from runner callback / AMQP-listener threads
     * and drained by HTTP request threads.
     */
    private val resultCache: Cache<String, TaskResult> = Caffeine.newBuilder()
        .maximumSize(MAX_RESULT_CACHE_SIZE.toLong())
        .expireAfterWrite(Duration.ofMinutes(RESULT_CACHE_TTL_MIN))
        .build()

    protected abstract val transportType: TransportType

    protected abstract val log: Logger

    protected abstract val adapter: NodeAdapter

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
            toQueueStatusView(nodeId, qs, engineConfig)
        }

    fun submitTask(data: TaskSubmitRequest): TaskSubmitResultView {
        val nodeId = data.nodeId
            ?: throw IllegalArgumentException("nodeId is required")
        return withNode(nodeId) { node ->
            val jobId = data.jobId ?: UUID.randomUUID().toString()
            listenerRegistry.register(jobId) { result ->
                resultCache.put(jobId, result)
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
                    threads = data.threads,
                    callbackUrl = resolveCallbackUrl(),
                    memoryLimitMb = data.memoryLimitMb,
                )
                val response = adapter.submitTask(node, task)
                TaskSubmitResultView(
                    jobId = response.jobId,
                    status = response.status,
                    position = response.position,
                    nodeId = response.nodeId,
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
                defaultThreads = data.defaultThreads,
                defaultWallTimeSec = data.defaultWallTimeSec,
                defaultCpuTimeSec = data.defaultCpuTimeSec,
                sandboxProcessMultiplier = data.sandboxProcessMultiplier,
            )
            val qs = adapter.updateConfig(node, request)
            val engineConfig = runCatching { adapter.queryNodeStatus(node).engineConfig }.getOrNull()
            toQueueStatusView(nodeId, qs, engineConfig)
        }
    }

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

    fun pollTaskResult(jobId: String): JobStatusView? {
        // asMap().remove is atomic and returns the previous value - equivalent to ConcurrentHashMap.remove.
        val result = resultCache.asMap().remove(jobId) ?: return null
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
            timestamp = result.timestamp,
        )
    }

    fun removeNode(nodeId: String): RemoveNodeView {
        val removed = nodeRegistry.deregister(nodeId)
        return RemoveNodeView(nodeId, removed)
    }

    private fun toQueueStatusView(
        nodeId: String,
        qs: com.nelos.parallel.commons.adapter.vo.response.QueueStatus,
        engineConfig: com.nelos.parallel.commons.adapter.vo.response.EngineConfig? = null,
    ) =
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

    protected open fun resolveCallbackUrl(): String? = null

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

    companion object {
        const val CALLBACK_URL = "/api/callback/result"
        private const val MAX_RESULT_CACHE_SIZE = 64
        private const val RESULT_CACHE_TTL_MIN = 10L
    }
}
