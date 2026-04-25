package com.nelos.parallel.adapters.http.controllers

import com.nelos.parallel.commons.adapter.listener.TaskResultListenerRegistry
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.gitlab.pipeline.service.PipelineService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller that receives task results from test-runner nodes via HTTP callback.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@RestController("prl.httpTaskResultCallbackController")
class HttpTaskResultCallbackController @Autowired constructor(
    private val pipelineService: PipelineService,
    private val listenerRegistry: TaskResultListenerRegistry,
) {

    @PutMapping("/api/callback/result")
    fun onResult(@RequestBody result: TaskResult): ResponseEntity<Void> {
        LOG.info("Received task result for job {} from node {}: {}", result.jobId, result.nodeId, result.status)
        pipelineService.handleResult(result)
        try {
            listenerRegistry.dispatch(result)  // adapter-test UI hook
        } catch (e: Exception) {
            LOG.warn("Adapter-test listener dispatch failed (ignored): {}", e.message)
        }
        return ResponseEntity.ok().build()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(HttpTaskResultCallbackController::class.java)
    }
}
