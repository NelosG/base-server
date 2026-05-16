package com.nelos.parallel.adapters.rabbit.listener

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.adapters.rabbit.RabbitConstants
import com.nelos.parallel.commons.adapter.vo.request.ProgressEvent
import com.nelos.parallel.pipeline.commons.service.PipelineService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

/**
 * Listens for runner progress events on [RabbitConstants.PROGRESS_QUEUE] and forwards
 * them to [PipelineService.handleProgress].
 *
 * Acknowledgement policy mirrors [RabbitResultListener]: malformed payloads are acked
 * (poison-pill protection), but processing failures (DB hiccup, etc.) propagate so the
 * broker can requeue the message for another attempt.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.rabbitProgressListener")
class RabbitProgressListener(
    private val objectMapper: ObjectMapper,
    private val pipelineService: PipelineService,
) {

    // NOTE: keep single-consumer (concurrency = default = 1). Progress log ordering
    // relies on monotonic INSERT IDs in prl_submission_log; parallel consumers would
    // interleave lines from the same submission. See sibling TODO in RabbitResultListener
    // for the result-queue scaling plan.
    @RabbitListener(queues = [RabbitConstants.PROGRESS_QUEUE])
    fun onMessage(message: ByteArray) {
        val event = try {
            objectMapper.readValue(message, ProgressEvent::class.java)
        } catch (e: JsonProcessingException) {
            LOG.warn("Discarding malformed progress event: {}", e.message)
            return
        }
        pipelineService.handleProgress(event)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RabbitProgressListener::class.java)
    }
}
