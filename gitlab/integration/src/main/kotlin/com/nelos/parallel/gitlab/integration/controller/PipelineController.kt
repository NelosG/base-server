package com.nelos.parallel.gitlab.integration.controller

import com.nelos.parallel.pipeline.commons.service.PipelineService
import com.nelos.parallel.pipeline.commons.vo.PipelineStatusResponse
import com.nelos.parallel.pipeline.commons.vo.PipelineSubmitRequest
import com.nelos.parallel.pipeline.commons.vo.PipelineSubmitResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * API for GitLab CI pipeline jobs. CI job script calls these endpoints
 * to submit a test execution and poll for results/logs.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@RestController("prl.pipelineController")
class PipelineController(
    private val pipelineService: PipelineService,
) {

    @PostMapping("/api/pipeline/submit")
    fun submit(@RequestBody request: PipelineSubmitRequest): ResponseEntity<PipelineSubmitResponse> {
        LOG.info("Pipeline submit: project={}, mr={}, user={}", request.projectPath, request.mrIid, request.username)
        return try {
            val response = pipelineService.submit(request)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            LOG.error("Pipeline submit failed: {}", e.message, e)
            ResponseEntity.badRequest().body(PipelineSubmitResponse(submissionId = -1, status = "error: ${e.message}"))
        }
    }

    @GetMapping("/api/pipeline/status/{submissionId}")
    fun status(@PathVariable submissionId: Long): ResponseEntity<PipelineStatusResponse> {
        return try {
            val response = pipelineService.getStatus(submissionId)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            LOG.error("Pipeline status check failed for submission {}: {}", submissionId, e.message)
            ResponseEntity.notFound().build()
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PipelineController::class.java)
    }
}
