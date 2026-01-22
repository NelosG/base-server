package com.nelos.parallel.adapters.http.impl

import com.nelos.parallel.adapters.http.HttpNodeAdapter
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.exceptions.AdapterException
import com.nelos.parallel.commons.adapter.vo.*
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * HTTP-based implementation of [com.nelos.parallel.commons.adapter.NodeAdapter].
 * Communicates with test-runner nodes via their REST API.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class HttpNodeAdapterImpl(
    private val restClient: RestClient
) : HttpNodeAdapter {

    override val transportType: TransportType = TransportType.HTTP

    override fun submitTask(node: NodeInfo, task: TaskSubmission): TaskSubmissionResponse =
        adapterCall("Failed to submit task to node ${node.nodeId}") {
            restClient.post()
                .uri("${node.baseUrl}/api/run")
                .authHeaders(node)
                .contentType(MediaType.APPLICATION_JSON)
                .body(task)
                .retrieve()
                .body<TaskSubmissionResponse>()
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    override fun queryJobStatus(node: NodeInfo, jobId: String): TaskResult =
        adapterCall("Failed to query job $jobId on node ${node.nodeId}") {
            restClient.get()
                .uri("${node.baseUrl}/api/jobs/$jobId")
                .authHeaders(node)
                .retrieve()
                .body<TaskResult>()
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    override fun cancelJob(node: NodeInfo, jobId: String): Boolean =
        try {
            restClient.delete()
                .uri("${node.baseUrl}/api/jobs/$jobId")
                .authHeaders(node)
                .retrieve()
                .toBodilessEntity()
            true
        } catch (e: Exception) {
            LOG.warn("Failed to cancel job {} on node {}: {}", jobId, node.nodeId, e.message)
            false
        }

    override fun queryNodeStatus(node: NodeInfo): NodeStatus =
        adapterCall("Failed to query status of node ${node.nodeId}") {
            restClient.get()
                .uri("${node.baseUrl}/api/node/status")
                .authHeaders(node)
                .retrieve()
                .body<NodeStatus>()
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    override fun healthCheck(node: NodeInfo): Boolean =
        try {
            restClient.get()
                .uri("${node.baseUrl}/api/health")
                .retrieve()
                .toBodilessEntity()
            true
        } catch (e: Exception) {
            LOG.debug("Health check failed for node {}: {}", node.nodeId, e.message)
            false
        }

    override fun queueStatus(node: NodeInfo): QueueStatus =
        adapterCall("Failed to query queue status of node ${node.nodeId}") {
            restClient.get()
                .uri("${node.baseUrl}/api/status")
                .authHeaders(node)
                .retrieve()
                .body<QueueStatus>()
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    override fun listAdapters(node: NodeInfo): List<AdapterInfo> =
        adapterCall("Failed to list adapters on node ${node.nodeId}") {
            restClient.get()
                .uri("${node.baseUrl}/api/adapters")
                .authHeaders(node)
                .retrieve()
                .body<List<AdapterInfo>>()
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    override fun listAvailableAdapters(node: NodeInfo): List<AdapterInfo> =
        adapterCall("Failed to list available adapters on node ${node.nodeId}") {
            restClient.get()
                .uri("${node.baseUrl}/api/adapters/available")
                .authHeaders(node)
                .retrieve()
                .body<List<AdapterInfo>>()
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    override fun loadAdapter(node: NodeInfo, name: String, config: Map<String, Any?>?): AdapterActionResult =
        adapterCall("Failed to load adapter '$name' on node ${node.nodeId}") {
            restClient.post()
                .uri("${node.baseUrl}/api/adapters/$name")
                .authHeaders(node)
                .contentType(MediaType.APPLICATION_JSON)
                .body(config ?: emptyMap<String, Any?>())
                .retrieve()
                .body<AdapterActionResult>()
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    override fun unloadAdapter(node: NodeInfo, name: String): AdapterActionResult =
        adapterCall("Failed to unload adapter '$name' from node ${node.nodeId}") {
            restClient.delete()
                .uri("${node.baseUrl}/api/adapters/$name")
                .authHeaders(node)
                .retrieve()
                .body<AdapterActionResult>()
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    private val NodeInfo.baseUrl: String
        get() {
            val h = if (':' in host) "[$host]" else host
            return "http://$h:$port"
        }

    private fun <S : RestClient.RequestHeadersSpec<S>> RestClient.RequestHeadersSpec<S>.authHeaders(
        node: NodeInfo
    ): S = headers { headers -> node.authToken?.let { headers.setBearerAuth(it) } }

    private inline fun <T> adapterCall(errorMessage: String, block: () -> T): T =
        try {
            block()
        } catch (e: AdapterException) {
            throw e
        } catch (e: Exception) {
            throw AdapterException("$errorMessage: ${e.message}", e)
        }

    companion object {
        private val LOG = LoggerFactory.getLogger(HttpNodeAdapterImpl::class.java)
    }
}