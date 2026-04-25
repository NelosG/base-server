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
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * RabbitMQ-based implementation of [com.nelos.parallel.commons.adapter.NodeAdapter].
 * Communicates with test-runner nodes via AMQP messaging.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.rabbitNodeAdapter")
class RabbitNodeAdapterImpl(
    private val rabbitTemplate: RabbitTemplate,
    // Short-timeout template used only for control RPCs (statusRequest,
    // queueStatus, cancelJob, list*, load*, etc.) - see RabbitTopologyConfig
    // for the reply-timeout setting. submitTask uses the default rabbitTemplate
    // because engine acceptance ack can take longer (queue insertion + position
    // assignment).
    @Qualifier("prl.controlRabbitTemplate")
    private val controlRabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
) : RabbitNodeAdapter {

    override val transportType: TransportType = TransportType.AMQP

    override fun submitTask(node: NodeInfo, task: TaskSubmission): TaskSubmissionResponse =
        adapterCall("Failed to submit task via AMQP to node ${node.nodeId}") {
            val routingKey = RabbitConstants.ROUTING_KEY_CORRECTNESS
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
            // Runner wraps the response in a JobInfo envelope - the full TaskResult
            // is nested under `result` only when the job is completed.
            val response = sendControlMessage("getJobInfo", node.nodeId, "jobId" to jobId)
            objectMapper.readValue(response, JobInfoEnvelope::class.java).toTaskResult()
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
            objectMapper.readValue(response, QueueStatus::class.java)
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
            val response = sendControlMessage("updateConfig", node.nodeId, "config" to config)
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

    override fun discoverNodes(timeoutMs: Long): List<NodeInfo> {
        val correlationId = UUID.randomUUID().toString()
        val replies = ConcurrentHashMap<String, NodeInfo>()
        return try {
            rabbitTemplate.connectionFactory.createConnection().use { connection ->
                connection.createChannel(false).use { channel ->
                    val replyQueue = channel.queueDeclare().queue
                    val consumerTag = channel.basicConsume(replyQueue, true, object : DefaultConsumer(channel) {
                        override fun handleDelivery(
                            consumerTag: String,
                            envelope: Envelope,
                            properties: AMQP.BasicProperties,
                            body: ByteArray,
                        ) {
                            if (properties.correlationId != correlationId) return
                            runCatching {
                                val status = objectMapper.readValue(body, NodeStatus::class.java)
                                replies[status.nodeId] = NodeInfo(
                                    nodeId = status.nodeId,
                                    capabilities = status.capabilities,
                                    transports = status.transports,
                                    resourceProviders = status.resourceProviders,
                                    registeredAt = Instant.now(),
                                )
                            }.onFailure { LOG.warn("Failed to parse discovery reply: {}", it.message) }
                        }
                    })
                    val request = mapOf("type" to "statusRequest")
                    val payload = objectMapper.writeValueAsBytes(request)
                    val props = AMQP.BasicProperties.Builder()
                        .contentType(MessageProperties.CONTENT_TYPE_JSON)
                        .replyTo(replyQueue)
                        .correlationId(correlationId)
                        .build()
                    channel.basicPublish(RabbitConstants.NODE_FANOUT_EXCHANGE, "", props, payload)
                    try {
                        Thread.sleep(timeoutMs)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        LOG.warn("Discovery wait interrupted; returning {} replies received so far", replies.size)
                    } finally {
                        // basicCancel is a synchronous AMQP RPC - skip it if we
                        // were interrupted (shutdown scenario) so we don't block
                        // for the broker's network timeout during JVM exit. The
                        // channel.close() invoked by the surrounding .use {}
                        // block will cancel the consumer server-side anyway.
                        if (!Thread.currentThread().isInterrupted) {
                            runCatching { channel.basicCancel(consumerTag) }
                        }
                    }
                }
            }
            replies.values.toList()
        } catch (e: Exception) {
            LOG.warn("Node discovery failed: {}", e.message)
            emptyList()
        }
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

        // Control RPCs go through the short-timeout template so health checks
        // and status queries against unresponsive nodes don't burn a 30s
        // Tomcat-thread window each.
        val response = controlRabbitTemplate.sendAndReceive(
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
