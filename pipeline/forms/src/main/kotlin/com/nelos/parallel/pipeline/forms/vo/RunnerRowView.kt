package com.nelos.parallel.pipeline.forms.vo

import com.fasterxml.jackson.annotation.JsonInclude
import com.nelos.parallel.pipeline.runner.service.RunnerType

/**
 * One row on the /runners admin page. Always carries the live runner
 * metadata (name + type + available probe); `enabled` / `priority` /
 * `settingsJson` come from the persisted `prl_runner_config` row when one
 * exists, otherwise reflect defaults (`enabled=false`, no settings).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RunnerRowView(
    val name: String,
    val type: RunnerType,
    /** Convenience for the UI - mirrors [RunnerType.hasSettings] without forcing the JS side to know the enum. */
    val hasSettings: Boolean,
    val available: Boolean,
    val enabled: Boolean,
    val priority: Int,
    val settingsJson: String?,
    val id: Long?,
)
