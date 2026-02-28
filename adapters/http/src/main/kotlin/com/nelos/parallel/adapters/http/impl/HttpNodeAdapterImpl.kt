package com.nelos.parallel.adapters.http.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.nelos.parallel.adapters.http.HttpNodeAdapter
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.exceptions.AdapterException
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.findHttpConfig
import com.nelos.parallel.commons.adapter.vo.request.*
import com.nelos.parallel.commons.adapter.vo.response.*
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
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper
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

    override fun cancelJob(node: NodeInfo, jobId: String): CancelJobResponse =
        adapterCall("Failed to cancel job $jobId on node ${node.nodeId}") {
            restClient.delete()
                .uri("${node.baseUrl}/api/jobs/$jobId")
                .authHeaders(node)
                .retrieve()
                .body<CancelJobResponse>()
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
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
                .body<AdapterListResponse>()
                ?.adapters
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    override fun listAvailableAdapters(node: NodeInfo): List<AdapterInfo> =
        adapterCall("Failed to list available adapters on node ${node.nodeId}") {
            restClient.get()
                .uri("${node.baseUrl}/api/adapters/available")
                .authHeaders(node)
                .retrieve()
                .body<AdapterListResponse>()
                ?.adapters
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    override fun loadAdapter(node: NodeInfo, name: String, config: ObjectNode?): AdapterActionResult =
        adapterCall("Failed to load adapter '$name' on node ${node.nodeId}") {
            restClient.post()
                .uri("${node.baseUrl}/api/adapters/$name")
                .authHeaders(node)
                .contentType(MediaType.APPLICATION_JSON)
                .body(config ?: objectMapper.createObjectNode())
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

    override fun updateConfig(node: NodeInfo, config: ConfigUpdateRequest): QueueStatus =
        adapterCall("Failed to update config on node ${node.nodeId}") {
            restClient.put()
                .uri("${node.baseUrl}/api/config")
                .authHeaders(node)
                .contentType(MediaType.APPLICATION_JSON)
                .body(config)
                .retrieve()
                .body<QueueStatus>()
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    override fun listResourceProviders(node: NodeInfo): List<ResourceProviderInfo> =
        adapterCall("Failed to list resource providers on node ${node.nodeId}") {
            restClient.get()
                .uri("${node.baseUrl}/api/resource-providers")
                .authHeaders(node)
                .retrieve()
                .body<ResourceProviderListResponse>()
                ?.providers
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    override fun listAvailableResourceProviders(node: NodeInfo): List<ResourceProviderInfo> =
        adapterCall("Failed to list available resource providers on node ${node.nodeId}") {
            restClient.get()
                .uri("${node.baseUrl}/api/resource-providers/available")
                .authHeaders(node)
                .retrieve()
                .body<ResourceProviderListResponse>()
                ?.providers
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    override fun loadResourceProvider(node: NodeInfo, name: String, config: ObjectNode?): ResourceProviderActionResult =
        adapterCall("Failed to load resource provider '$name' on node ${node.nodeId}") {
            restClient.post()
                .uri("${node.baseUrl}/api/resource-providers/$name")
                .authHeaders(node)
                .contentType(MediaType.APPLICATION_JSON)
                .body(config ?: objectMapper.createObjectNode())
                .retrieve()
                .body<ResourceProviderActionResult>()
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    override fun unloadResourceProvider(node: NodeInfo, name: String): ResourceProviderActionResult =
        adapterCall("Failed to unload resource provider '$name' from node ${node.nodeId}") {
            restClient.delete()
                .uri("${node.baseUrl}/api/resource-providers/$name")
                .authHeaders(node)
                .retrieve()
                .body<ResourceProviderActionResult>()
                ?: throw AdapterException("Empty response from node ${node.nodeId}")
        }

    private val NodeInfo.baseUrl: String
        get() {
            val config = findHttpConfig()
                ?: error("Node $nodeId has no HTTP transport config")
            val host = config.host ?: error("Node $nodeId HTTP config has no host")
            val port = config.port ?: error("Node $nodeId HTTP config has no port")
            val h = if (':' in host) "[$host]" else host
            return "http://$h:$port"
        }

    private fun <S : RestClient.RequestHeadersSpec<S>> RestClient.RequestHeadersSpec<S>.authHeaders(
        node: NodeInfo
    ): S = headers { headers -> node.findHttpConfig()?.authToken?.let { headers.setBearerAuth(it) } }

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
