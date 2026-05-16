package com.nelos.parallel.pipeline.commons.service

import com.nelos.parallel.commons.adapter.vo.request.ProgressEvent
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.pipeline.commons.vo.PipelineStatusResponse
import com.nelos.parallel.pipeline.commons.vo.PipelineSubmitRequest
import com.nelos.parallel.pipeline.commons.vo.PipelineSubmitResponse

/**
 * Service for GitLab CI pipeline integration. Handles task submission
 * from CI jobs and provides status/log polling.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface PipelineService {

    fun submit(request: PipelineSubmitRequest): PipelineSubmitResponse

    fun getStatus(submissionId: Long): PipelineStatusResponse

    /**
     * Idempotent terminal callback. Called from any transport (AMQP listener, HTTP
     * controller). Looks up the [TaskResult.jobId], skips duplicates already in a
     * terminal status, otherwise persists the final result and updates submission
     * status.
     */
    fun handleResult(result: TaskResult)

    /**
     * Best-effort incremental log line from the runner. Skips events for unknown
     * jobIds and already-finished submissions.
     */
    fun handleProgress(event: ProgressEvent)
}
