package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Aggregate performance metrics for a scenario.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ScenarioMetrics @JsonCreator constructor(
    @param:JsonProperty("t1Ms") val t1Ms: Double,
    @param:JsonProperty("tpMs") val tpMs: Double,
    @param:JsonProperty("speedup") val speedup: Double,
    @param:JsonProperty("efficiency") val efficiency: Double,
)
