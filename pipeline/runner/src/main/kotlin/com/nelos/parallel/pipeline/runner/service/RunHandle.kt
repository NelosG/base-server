package com.nelos.parallel.pipeline.runner.service

/**
 * Opaque handle returned by [TaskRunner.tryRun] when a task is accepted.
 * Used by the manager to cancel in-flight work without knowing the underlying
 * transport (kill local process, docker kill, adapter.cancelJob).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface RunHandle {

    val runnerName: String

    val jobId: String

    fun cancel()
}
