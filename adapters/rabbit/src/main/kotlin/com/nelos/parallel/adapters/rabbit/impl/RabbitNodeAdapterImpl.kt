package com.nelos.parallel.adapters.rabbit.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.nelos.parallel.adapters.rabbit.RabbitConstants
import com.nelos.parallel.adapters.rabbit.RabbitNodeAdapter
import com.nelos.parallel.adapters.rabbit.exceptions.RabbitAdapterException
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.request.*
import com.nelos.parallel.commons.adapter.vo.response.*
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate

/**
 * RabbitMQ-based implementation of [com.nelos.parallel.commons.adapter.NodeAdapter].
 * Communicates with test-runner nodes via AMQP messaging.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class RabbitNodeAdapterImpl(
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper
) : RabbitNodeAdapter {

    override val transportType: TransportType = TransportType.AMQP

    override fun submitTask(node: NodeInfo, task: TaskSubmission): TaskSubmissionResponse =
        adapterCall("Failed to submit task via AMQP to node ${node.nodeId}") {
            val routingKey = task.mode?.toValue() ?: RabbitConstants.ROUTING_KEY_CORRECTNESS
            val message = jsonMessage(task) {
                setHeader("nodeId", node.nodeId)
            }

            val response = rabbitTemplate.sendAndReceive(
                RabbitConstants.TEST_DIRECT_EXCHANGE, routingKey, message
            ) ?: throw RabbitAdapterException(
                "No response received for task submission to node ${node.nodeId}"
            )

            objectMapper.readValue(response.body, TaskSubmissionResponse::class.java)
        }

    override fun queryJobStatus(node: NodeInfo, jobId: String): TaskResult =
        adapterCall("Failed to query job $jobId on node ${node.nodeId}") {
            val response = sendControlMessage("getJobInfo", node.nodeId, "jobId" to jobId)
            objectMapper.readValue(response, TaskResult::class.java)
        }

    override fun cancelJob(node: NodeInfo, jobId: String): CancelJobResponse =
        adapterCall("Failed to cancel job $jobId on node ${node.nodeId}") {
            val response = sendControlMessage("cancelJob", node.nodeId, "jobId" to jobId)
            objectMapper.readValue(response, CancelJobResponse::class.java)
        }

    override fun queryNodeStatus(node: NodeInfo): NodeStatus =
        adapterCall("Failed to query status of node ${node.nodeId}") {
            val response = sendControlMessage("statusRequest", node.nodeId)
            objectMapper.readValue(response, NodeStatus::class.java)
        }

    override fun healthCheck(node: NodeInfo): Boolean =
        try {
            sendControlMessage("statusRequest", node.nodeId)
            true
        } catch (e: Exception) {
            LOG.debug("Health check failed for node {}: {}", node.nodeId, e.message)
            false
        }

    override fun queueStatus(node: NodeInfo): QueueStatus =
        adapterCall("Failed to query queue status of node ${node.nodeId}") {
            val response = sendControlMessage("queueStatus", node.nodeId)
            val tree = objectMapper.readTree(response)
            val queue = tree.get("queue") ?: tree

            objectMapper.readValue(queue.traverse(), QueueStatus::class.java)
        }

    override fun listAdapters(node: NodeInfo): List<AdapterInfo> =
        fetchAdapterList("listAdapters", node.nodeId)

    override fun listAvailableAdapters(node: NodeInfo): List<AdapterInfo> =
        fetchAdapterList("listAvailableAdapters", node.nodeId)

    private fun fetchAdapterList(command: String, nodeId: String): List<AdapterInfo> =
        adapterCall("Failed to $command on node $nodeId") {
            val response = sendControlMessage(command, nodeId)
            val tree = objectMapper.readTree(response)
            val adapters = tree.get("adapters")
                ?: throw RabbitAdapterException("Missing 'adapters' field in response")
            objectMapper.readValue(
                adapters.traverse(),
                objectMapper.typeFactory.constructCollectionType(List::class.java, AdapterInfo::class.java)
            )
        }

    override fun loadAdapter(node: NodeInfo, name: String, config: ObjectNode?): AdapterActionResult =
        adapterCall("Failed to load adapter '$name' on node ${node.nodeId}") {
            val response = sendControlMessage(
                "loadAdapter", node.nodeId,
                "adapter" to name, "config" to (config ?: objectMapper.createObjectNode())
            )
            objectMapper.readValue(response, AdapterActionResult::class.java)
        }

    override fun unloadAdapter(node: NodeInfo, name: String): AdapterActionResult =
        adapterCall("Failed to unload adapter '$name' from node ${node.nodeId}") {
            val response = sendControlMessage("unloadAdapter", node.nodeId, "adapter" to name)
            objectMapper.readValue(response, AdapterActionResult::class.java)
        }

    override fun updateConfig(node: NodeInfo, config: ConfigUpdateRequest): QueueStatus =
        adapterCall("Failed to update config on node ${node.nodeId}") {
            val configMap = buildMap<String, Any?> {
                config.maxCorrectnessWorkers?.let { put("maxCorrectnessWorkers", it) }
                config.jobRetentionSeconds?.let { put("jobRetentionSeconds", it) }
            }
            val response = sendControlMessage("updateConfig", node.nodeId, "config" to configMap)
            objectMapper.readValue(response, QueueStatus::class.java)
        }

    override fun listResourceProviders(node: NodeInfo): List<ResourceProviderInfo> =
        fetchProviderList("listResourceProviders", node.nodeId)

    override fun listAvailableResourceProviders(node: NodeInfo): List<ResourceProviderInfo> =
        fetchProviderList("listAvailableResourceProviders", node.nodeId)

    private fun fetchProviderList(command: String, nodeId: String): List<ResourceProviderInfo> =
        adapterCall("Failed to $command on node $nodeId") {
            val response = sendControlMessage(command, nodeId)
            val tree = objectMapper.readTree(response)
            val providers = tree.get("providers")
                ?: throw RabbitAdapterException("Missing 'providers' field in response")
            objectMapper.readValue(
                providers.traverse(),
                objectMapper.typeFactory.constructCollectionType(List::class.java, ResourceProviderInfo::class.java)
            )
        }

    override fun loadResourceProvider(node: NodeInfo, name: String, config: ObjectNode?): ResourceProviderActionResult =
        adapterCall("Failed to load resource provider '$name' on node ${node.nodeId}") {
            val response = sendControlMessage(
                "loadResourceProvider", node.nodeId,
                "provider" to name, "config" to (config ?: objectMapper.createObjectNode())
            )
            objectMapper.readValue(response, ResourceProviderActionResult::class.java)
        }

    override fun unloadResourceProvider(node: NodeInfo, name: String): ResourceProviderActionResult =
        adapterCall("Failed to unload resource provider '$name' from node ${node.nodeId}") {
            val response = sendControlMessage("unloadResourceProvider", node.nodeId, "provider" to name)
            objectMapper.readValue(response, ResourceProviderActionResult::class.java)
        }

    private fun sendControlMessage(
        type: String,
        nodeId: String,
        vararg extra: Pair<String, Any?>
    ): ByteArray {
        val request = buildMap {
            put("type", type)
            put("nodeId", nodeId)
            extra.forEach { (k, v) -> put(k, v) }
        }

        val response = rabbitTemplate.sendAndReceive(
            RabbitConstants.NODE_CONTROL_EXCHANGE, nodeId, jsonMessage(request)
        )

        return response?.body
            ?: throw RabbitAdapterException("No response received for control message: $type")
    }

    private fun jsonMessage(
        payload: Any,
        configureProperties: MessageProperties.() -> Unit = {}
    ): Message {
        val properties = MessageProperties().apply {
            contentType = MessageProperties.CONTENT_TYPE_JSON
            configureProperties()
        }
        return Message(objectMapper.writeValueAsBytes(payload), properties)
    }

    private inline fun <T> adapterCall(errorMessage: String, block: () -> T): T =
        try {
            block()
        } catch (e: RabbitAdapterException) {
            throw e
        } catch (e: Exception) {
            throw RabbitAdapterException("$errorMessage: ${e.message}", e)
        }

    companion object {
        private val LOG = LoggerFactory.getLogger(RabbitNodeAdapterImpl::class.java)
    }
}
