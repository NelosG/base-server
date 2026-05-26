package com.nelos.parallel.pipeline.runner.service

/**
 * Reads the runner-specific JSON settings blob persisted in
 * `prl_runner_config.settings` and deserializes it into a typed VO. Each
 * runner declares its own VO (e.g. `LocalRunnerSettings`,
 * `DockerRunnerSettings`); the provider returns `defaults` when the row is
 * absent or the JSON cannot be mapped onto the requested shape.
 *
 * Runners call this on every dispatch - the implementation is expected to
 * cache results locally so the DB isn't hit per-job.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface RunnerSettingsProvider {

    fun <T : Any> get(runnerName: String, type: Class<T>, defaults: () -> T): T
}
