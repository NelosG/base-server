package com.nelos.parallel.adapters.rabbit.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.adapters.rabbit.RabbitConstants
import com.nelos.parallel.commons.adapter.listener.TaskResultListener
import com.nelos.parallel.commons.adapter.vo.TaskResult
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Listens for task results on the [com.nelos.parallel.adapters.rabbit.RabbitConstants.RESULTS_QUEUE] and dispatches
 * them to registered [TaskResultListener] instances.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.rabbitResultListener")
class RabbitResultListener @Autowired constructor(
    private val objectMapper: ObjectMapper
) {

    @Autowired(required = false)
    private var listeners: List<TaskResultListener> = emptyList()

    @RabbitListener(queues = [RabbitConstants.RESULTS_QUEUE])
    fun onMessage(message: ByteArray) {
        try {
            val result = objectMapper.readValue(message, TaskResult::class.java)
            LOG.info(
                "Received AMQP task result for job {} from node {}: {}",
                result.jobId,
                result.nodeId,
                result.status
            )

            listeners.forEach { listener ->
                runCatching { listener.onTaskResult(result) }
                    .onFailure { LOG.error("Error in task result listener: {}", it.message, it) }
            }
        } catch (e: Exception) {
            LOG.error("Failed to deserialize task result message: {}", e.message, e)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RabbitResultListener::class.java)
    }
}
