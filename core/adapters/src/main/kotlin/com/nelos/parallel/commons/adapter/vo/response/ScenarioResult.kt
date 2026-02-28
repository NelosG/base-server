package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Result of a test scenario containing named tests and optional metrics.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ScenarioResult @JsonCreator constructor(
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("tests") val tests: List<TestEntry>,
    @param:JsonProperty("metrics") val metrics: ScenarioMetrics? = null,
)
