package com.nelos.parallel.adapters.rabbit.listener

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.adapters.rabbit.RabbitConstants
import com.nelos.parallel.commons.adapter.listener.TaskResultListenerRegistry
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.pipeline.commons.service.PipelineService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Listens for task results on the [RabbitConstants.RESULTS_QUEUE] and forwards them
 * to [PipelineService] for idempotent terminal processing.
 *
 * Acknowledgement: bad-payload errors swallow (ack) so the queue does not loop on
 * a poison message; processing failures (DB down, etc.) propagate so the broker
 * requeues for another consumer (default `defaultRequeueRejected=true`).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.rabbitResultListener")
class RabbitResultListener @Autowired constructor(
    private val objectMapper: ObjectMapper,
    private val pipelineService: PipelineService,
    private val listenerRegistry: TaskResultListenerRegistry,
) {

    // TODO(perf): switch to `@RabbitListener(queues = [...], concurrency = "2-4")` once
    //  burst load (50+ concurrent results) becomes a real bottleneck. Default = 1 consumer,
    //  so all final results are serialized through a single thread. Before enabling:
    //   1) make `pipelineService.handleResult` idempotent - early-return when submission.status
    //      is already in TERMINAL_STATUSES (protects against broker re-delivery and any
    //      race between two concurrent consumers picking up the same message after requeue);
    //   2) bump `spring.rabbitmq.listener.simple.prefetch` to 4-8 so each consumer prefetches
    //      a batch instead of pulling one-by-one;
    //   3) verify Hikari pool stays comfortable (current max=30 covers 8 consumers x 1 conn).
    //  Don't bump concurrency on RabbitProgressListener - progress log ordering relies on
    //  monotonic INSERT IDs in prl_submission_log; parallel consumers would interleave lines
    //  from the same submission.
    @RabbitListener(queues = [RabbitConstants.RESULTS_QUEUE])
    fun onMessage(message: ByteArray) {
        val result = try {
            objectMapper.readValue(message, TaskResult::class.java)
        } catch (e: JsonProcessingException) {
            LOG.error("Discarding malformed task result message: {}", e.message)
            return
        }
        LOG.info(
            "Received AMQP task result for job {} from node {}: {}",
            result.jobId, result.nodeId, result.status
        )
        pipelineService.handleResult(result)
        try {
            listenerRegistry.dispatch(result)  // adapter-test UI hook
        } catch (e: Exception) {
            LOG.warn("Adapter-test listener dispatch failed (ignored): {}", e.message)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RabbitResultListener::class.java)
    }
}
