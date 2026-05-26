package com.nelos.parallel.adapters.http.controllers

import com.nelos.parallel.commons.adapter.vo.request.ProgressEvent
import com.nelos.parallel.pipeline.commons.service.PipelineService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller that receives runner progress events via HTTP. Best-effort -
 * any error is logged but never fails the request, so the runner does not retry.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@RestController("prl.pipelineProgressController")
class PipelineProgressController(
    private val pipelineService: PipelineService,
) {

    @PostMapping("/api/callback/progress")
    fun onProgress(@RequestBody event: ProgressEvent): ResponseEntity<Void> {
        try {
            pipelineService.handleProgress(event)
        } catch (e: Exception) {
            LOG.warn("Failed to handle progress event for job {}: {}", event.jobId, e.message)
        }
        return ResponseEntity.ok().build()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PipelineProgressController::class.java)
    }
}
