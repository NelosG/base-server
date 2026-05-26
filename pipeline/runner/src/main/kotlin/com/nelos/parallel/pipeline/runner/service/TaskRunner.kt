package com.nelos.parallel.pipeline.runner.service

/**
 * One way to physically execute a [com.nelos.parallel.commons.adapter.vo.request.TaskSubmission].
 *
 * Implementations are plain Spring beans collected by [RunnerManager]. Whether
 * a runner participates in dispatch is decided by configuration + the runner's
 * own [isAvailable] check; the manager applies the priority/round-robin/
 * fallback policy on top.
 *
 * Async vs sync transports are hidden behind [tryRun] + [RunnerContext.onResult]:
 * an HTTP/AMQP runner returns immediately and fires [RunnerContext.onResult]
 * later from its callback listener; a local/docker runner schedules the
 * process on a background pool and fires [RunnerContext.onResult] when the
 * process exits.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface TaskRunner {

    val type: RunnerType

    /** Stable identifier - same value as in `prl_runner_config.name`. */
    val name: String

    /**
     * Fast soft-check: does this runner currently have *any* capacity to accept
     * a task. Cheap probes only - no network calls, no expensive IO. The
     * authoritative decision is in [tryRun].
     */
    fun isAvailable(): Boolean

    /**
     * Try to accept the task.
     *
     *  - returns a [RunHandle] when the task was successfully started/queued.
     *    The runner now owns the responsibility to invoke
     *    [RunnerContext.onResult] exactly once.
     *  - returns `null` when this runner cannot accept the task right now
     *    (no healthy nodes, pool saturated, binary missing on disk, etc).
     *    The manager falls over to the next runner.
     *  - throws [com.nelos.parallel.pipeline.runner.exception.RunnerInfraException]
     *    for the same outcome when null isn't expressive enough (e.g. needs
     *    a message). Manager treats it identically to null.
     *
     * Any other exception propagates out as a hard failure - the manager will
     * log it and try the next runner anyway, but the runner author should
     * prefer `RunnerInfraException` for the expected fallback path.
     */
    fun tryRun(ctx: RunnerContext): RunHandle?
}
