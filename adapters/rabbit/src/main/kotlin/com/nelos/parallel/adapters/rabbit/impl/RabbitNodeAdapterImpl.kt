package com.nelos.parallel.adapters.rabbit.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.adapters.rabbit.RabbitConstants
import com.nelos.parallel.adapters.rabbit.RabbitNodeAdapter
import com.nelos.parallel.adapters.rabbit.exceptions.RabbitAdapterException
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.*
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
            val routingKey = task.mode ?: "correctness"
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

    override fun cancelJob(node: NodeInfo, jobId: String): Boolean =
        try {
            val response = sendControlMessage("cancelJob", node.nodeId, "jobId" to jobId)
            val tree = objectMapper.readTree(response)
            tree.path("status").asText() == "cancelled"
        } catch (e: Exception) {
            LOG.warn("Failed to cancel job {} on node {}: {}", jobId, node.nodeId, e.message)
            false
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

    override fun loadAdapter(node: NodeInfo, name: String, config: Map<String, Any?>?): AdapterActionResult =
        adapterCall("Failed to load adapter '$name' on node ${node.nodeId}") {
            val response = sendControlMessage(
                "loadAdapter", node.nodeId,
                "adapter" to name, "config" to (config ?: emptyMap<String, Any?>())
            )
            objectMapper.readValue(response, AdapterActionResult::class.java)
        }

    override fun unloadAdapter(node: NodeInfo, name: String): AdapterActionResult =
        adapterCall("Failed to unload adapter '$name' from node ${node.nodeId}") {
            val response = sendControlMessage("unloadAdapter", node.nodeId, "adapter" to name)
            objectMapper.readValue(response, AdapterActionResult::class.java)
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
            RabbitConstants.NODE_FANOUT_EXCHANGE, "", jsonMessage(request)
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
