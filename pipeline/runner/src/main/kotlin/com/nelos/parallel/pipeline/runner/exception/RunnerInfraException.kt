package com.nelos.parallel.pipeline.runner.exception

/**
 * Thrown by a [com.nelos.parallel.pipeline.runner.service.TaskRunner] to
 * signal "I can't accept this task" - the manager will fall over to the next
 * runner in the priority chain. Use this instead of `null` from `tryRun` when
 * a message helps diagnostics.
 *
 * Do NOT throw this for task-level failures (build error, test failure, ...):
 * those are normal results delivered through the result callback and should
 * not trigger failover.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class RunnerInfraException(
    val runnerName: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException("[$runnerName] $message", cause)
