package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Statistical metrics for a single test run.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class RunStats @JsonCreator constructor(
    @param:JsonProperty("timeMs") val timeMs: Double,
    @param:JsonProperty("workMs") val workMs: Double,
    @param:JsonProperty("spanMs") val spanMs: Double,
    @param:JsonProperty("parallelism") val parallelism: Double,
    @param:JsonProperty("speedup") val speedup: Double,
    @param:JsonProperty("efficiency") val efficiency: Double,
)
