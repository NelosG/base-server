package com.nelos.parallel.pipeline.runner.service

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 *
 * @property hasSettings `true` when the admin UI should expose an editable
 *   settings JSON field for this runner. Remote-transport runners (HTTP /
 *   AMQP) read their live state from `prl_node` plus the per-transport node
 *   config pages, so `prl_runner_config.settings` is unused for them.
 */
enum class RunnerType(val hasSettings: Boolean) {
    LOCAL(true),
    DOCKER(true),
    HTTP(false),
    AMQP(false),
}
