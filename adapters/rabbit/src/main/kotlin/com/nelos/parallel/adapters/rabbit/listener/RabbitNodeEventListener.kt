package com.nelos.parallel.adapters.rabbit.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.adapters.rabbit.RabbitConstants
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.NodeRegistrationRequest
import com.nelos.parallel.commons.adapter.vo.NodeRegistrationResponse
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

/**
 * Listens for node registration and deregistration events on the
 * [com.nelos.parallel.adapters.rabbit.RabbitConstants.NODE_EVENTS_QUEUE] and updates the [NodeRegistry].
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.rabbitNodeEventListener")
class RabbitNodeEventListener @Autowired constructor(
    private val objectMapper: ObjectMapper,
    private val nodeRegistry: NodeRegistry,
    private val rabbitTemplate: RabbitTemplate
) {

    @RabbitListener(queues = [RabbitConstants.NODE_EVENTS_QUEUE])
    fun onMessage(message: Message) {
        try {
            val request = objectMapper.readValue(message.body, NodeRegistrationRequest::class.java)
            handleEvent(request, message.messageProperties)
        } catch (e: Exception) {
            LOG.error("Failed to process node event message: {}", e.message, e)
        }
    }

    private fun handleEvent(request: NodeRegistrationRequest, properties: MessageProperties) {
        when (request.type ?: NodeRegistrationRequest.DEFAULT_TYPE) {
            NodeRegistrationRequest.TYPE_REGISTER -> {
                val nodeInfo = NodeInfo(
                    nodeId = request.nodeId,
                    transport = request.transport ?: TransportType.AMQP,
                    host = request.host ?: "unknown",
                    port = request.port,
                    authToken = request.authToken,
                    capabilities = request.capabilities ?: emptyMap(),
                    registeredAt = Instant.now()
                )
                nodeRegistry.register(nodeInfo)
                LOG.info(
                    "Node registered via AMQP: {} (timestamp={}, currentLoad={})",
                    request.nodeId, request.timestamp, request.currentLoad
                )

                sendReply(properties, NodeRegistrationResponse(
                    status = "registered",
                    nodeId = request.nodeId,
                    orchestratorAuthToken = UUID.randomUUID().toString(),
                ))
            }

            NodeRegistrationRequest.TYPE_DEREGISTER -> {
                nodeRegistry.deregister(request.nodeId)
                LOG.info("Node deregistered via AMQP: {}", request.nodeId)

                sendReply(properties, NodeRegistrationResponse(
                    status = "deregistered",
                    nodeId = request.nodeId,
                ))
            }

            else -> LOG.warn("Unknown node event type: {}", request.type)
        }
    }

    private fun sendReply(properties: MessageProperties, response: Any) {
        val replyTo = properties.replyTo ?: return
        val replyProperties = MessageProperties().apply {
            contentType = MessageProperties.CONTENT_TYPE_JSON
            correlationId = properties.correlationId
        }
        val replyMessage = Message(objectMapper.writeValueAsBytes(response), replyProperties)
        rabbitTemplate.send("", replyTo, replyMessage)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RabbitNodeEventListener::class.java)
    }
}
