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
    // Per-scenario aggregate of the same shape as job-wide ResultSummary.<group>,
    // computed over this scenario's runs. metrics stays - it's a single T1-vs-Tp
    // pair, summary carries the full thread-count ladder.
    @param:JsonProperty("summary") val summary: TestSummary? = null,
)
