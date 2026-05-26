package com.nelos.parallel.pipeline.runner.service

import com.nelos.parallel.commons.adapter.vo.request.TaskSubmission
import com.nelos.parallel.commons.adapter.vo.response.TaskResult

/**
 * Everything a [TaskRunner] needs to launch one submission and report its
 * completion back to the pipeline.
 *
 * The [onResult] callback must be invoked exactly once by the runner that
 * accepted the task, whether the engine completed normally, errored, or was
 * cancelled. Synchronous runners (local exe, docker) call it from the
 * background thread after the process exits; remote runners (HTTP, AMQP)
 * call it from the existing callback / reply listener.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
data class RunnerContext(
    val submissionId: Long,
    val task: TaskSubmission,
    val onResult: (TaskResult) -> Unit,
)
