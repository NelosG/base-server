package com.nelos.parallel.dev.view.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.TaskSubmission
import com.nelos.parallel.dev.view.vo.*
import com.nelos.parallel.dev.vo.AdapterRequest
import com.nelos.parallel.dev.vo.JobQueryRequest
import com.nelos.parallel.dev.vo.TaskSubmitRequest
import org.slf4j.Logger

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
) {

    protected abstract val transportType: TransportType

    protected abstract val log: Logger

    protected abstract val adapter: NodeAdapter

    fun getNodes(): List<NodeView> =
        nodeRegistry.findByTransport(transportType).map { it.toView() }

    fun refreshAndPruneNodes(): List<NodeView> {
        nodeRegistry.findByTransport(transportType)
            .filterNot { runCatching { adapter.healthCheck(it) }.getOrDefault(false) }
            .forEach { node ->
                nodeRegistry.deregister(node.nodeId)
                log.info("Pruned dead {} node: {}", transportType.name, node.nodeId)
            }
        return getNodes()
    }

    fun healthCheck(nodeId: String): Any {
        val node = nodeRegistry.findById(nodeId)
            ?: return errorView("Node not found: $nodeId")
        return HealthCheckView(nodeId, adapter.healthCheck(node))
    }

    fun pollNodeStatus(nodeId: String): Any =
        withNode(nodeId) { node ->
            val status = adapter.queryNodeStatus(node)
            NodeStatusView(
                nodeId = status.nodeId,
                transport = status.transport?.name ?: transportType.name,
                port = status.port,
                capabilities = status.capabilities ?: emptyMap(),
                queue = status.effectiveQueue(),
            )
        }

    fun queueStatus(nodeId: String): Any =
        withNode(nodeId) { node ->
            val qs = adapter.queueStatus(node)
            QueueStatusView(
                nodeId = nodeId,
                correctnessQueue = qs.effectiveCorrectnessQueue()?.let {
                    QueueInfoView(it.queued, it.running, it.totalWorkers)
                },
                performanceQueue = qs.effectivePerformanceQueue()?.let {
                    QueueInfoView(it.queued, it.running, it.totalWorkers)
                },
            )
        }

    fun submitTask(data: TaskSubmitRequest): Any {
        val nodeId = data.nodeId
            ?: return errorView("nodeId is required")
        return withNode(nodeId) { node ->
            val task = TaskSubmission(
                jobId = data.jobId,
                testId = data.testId ?: "",
                solutionGitUrl = data.solutionGitUrl,
                solutionDir = data.solutionDir,
                testsGitUrl = data.testsGitUrl,
                testsDir = data.testsDir,
                mode = data.mode ?: "correctness",
                threads = data.threads,
                callbackUrl = resolveCallbackUrl(),
            )
            val response = adapter.submitTask(node, task)
            TaskSubmitResultView(response.jobId, response.status, response.position)
        }
    }

    fun queryJobStatus(data: JobQueryRequest): Any {
        val nodeId = data.nodeId ?: return errorView("nodeId is required")
        val jobId = data.jobId ?: return errorView("jobId is required")
        return withNode(nodeId) { node ->
            val result = adapter.queryJobStatus(node, jobId)
            JobStatusView(
                jobId = result.jobId,
                nodeId = result.nodeId,
                status = result.status,
                result = result.result,
                error = result.error,
                durationMs = result.durationMs,
                mode = result.mode,
                buildOutput = result.buildOutput,
            )
        }
    }

    fun cancelJob(data: JobQueryRequest): Any {
        val nodeId = data.nodeId ?: return errorView("nodeId is required")
        val jobId = data.jobId ?: return errorView("jobId is required")
        return withNode(nodeId) { node ->
            val cancelled = adapter.cancelJob(node, jobId)
            CancelJobView(jobId, cancelled)
        }
    }

    fun listAdapters(nodeId: String): Any =
        withNode(nodeId) { node ->
            adapter.listAdapters(node).map { AdapterView(it.name, it.status, it.dllPath) }
        }

    fun listAvailableAdapters(nodeId: String): Any =
        withNode(nodeId) { node ->
            adapter.listAvailableAdapters(node).map { AdapterView(it.name, it.status, it.dllPath) }
        }

    fun loadAdapter(data: AdapterRequest): Any {
        val nodeId = data.nodeId ?: return errorView("nodeId is required")
        val adapterName = data.adapterName ?: return errorView("adapterName is required")
        return withNode(nodeId) { node ->
            val config = parseConfig(data.config)
            val result = adapter.loadAdapter(node, adapterName, config)
            AdapterActionView(result.adapter, result.status, result.error)
        }
    }

    fun unloadAdapter(data: AdapterRequest): Any {
        val nodeId = data.nodeId ?: return errorView("nodeId is required")
        val adapterName = data.adapterName ?: return errorView("adapterName is required")
        return withNode(nodeId) { node ->
            val result = adapter.unloadAdapter(node, adapterName)
            AdapterActionView(result.adapter, result.status, result.error)
        }
    }

    fun removeNode(nodeId: String): RemoveNodeView {
        val removed = nodeRegistry.deregister(nodeId)
        return RemoveNodeView(nodeId, removed)
    }

    protected open fun resolveCallbackUrl(): String? = null

    protected fun errorView(message: String): ErrorView = ErrorView(message)

    protected fun <T> withNode(nodeId: String, action: (NodeInfo) -> T): Any {
        val node = nodeRegistry.findById(nodeId)
            ?: return errorView("Node not found: $nodeId")
        return runCatching { action(node) }.fold(
            onSuccess = { result -> result as Any },
            onFailure = { e -> errorView(e.message ?: "Unknown error") }
        )
    }

    protected fun NodeInfo.toView(): NodeView = NodeView(
        nodeId = nodeId,
        host = host,
        port = port,
        capabilities = capabilities ?: emptyMap(),
        registeredAt = registeredAt.toString(),
    )

    @Suppress("UNCHECKED_CAST")
    private fun parseConfig(json: String?): Map<String, Any?>? {
        if (json.isNullOrBlank() || json == "{}") return null
        return runCatching {
            objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
        }.getOrNull()
    }

    companion object {
        const val CALLBACK_URL = "/api/callback/result"
    }
}
