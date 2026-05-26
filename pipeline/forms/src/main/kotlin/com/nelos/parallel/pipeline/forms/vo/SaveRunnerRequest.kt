package com.nelos.parallel.pipeline.forms.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Payload of the /runners save button. The view service validates that
 * `settingsJson` parses as JSON and writes it into `prl_runner_config.settings`.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class SaveRunnerRequest @JsonCreator constructor(
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("enabled") val enabled: Boolean,
    @param:JsonProperty("priority") val priority: Int,
    /** Raw JSON object. Empty/blank means "clear settings". */
    @param:JsonProperty("settingsJson") val settingsJson: String?,
)
