package com.nelos.parallel.pipeline.runner.service

/**
 * Pluggable dispatcher above [TaskRunner]. Discovers runner beans from the
 * Spring context, filters by configuration, sorts by priority (ascending),
 * round-robins between equal-priority runners, and falls over to the next on
 * infrastructure failure.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface RunnerManager {

    /**
     * Dispatch one task. Returns the [RunHandle] of the runner that accepted
     * it. Throws
     * [com.nelos.parallel.pipeline.runner.exception.NoRunnerAvailableException]
     * if every configured runner declined.
     */
    fun dispatch(ctx: RunnerContext): RunHandle

    /**
     * Snapshot of all runner beans currently visible to the manager, with the
     * effective configuration applied. Used by the admin UI to render the
     * runners page; runners present as beans but missing from configuration
     * appear as disabled (`enabled = false`).
     */
    fun listRunners(): List<RunnerSnapshot>

    data class RunnerSnapshot(
        val name: String,
        val type: RunnerType,
        val enabled: Boolean,
        val priority: Int,
        val available: Boolean,
    )
}
