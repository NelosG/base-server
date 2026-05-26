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
    @param:JsonProperty("speedup") val speedup: Double,
    @param:JsonProperty("efficiency") val efficiency: Double,
    // Monitoring-derived metrics: only present when the engine actually
    // measured work/span (OpenMP monitor or stress mode). For cilk/parlay/seq
    // and OpenMP in performance mode (monitor=normal) these are absent.
    @param:JsonProperty("workMs") val workMs: Double? = null,
    @param:JsonProperty("spanMs") val spanMs: Double? = null,
    @param:JsonProperty("parallelism") val parallelism: Double? = null,
    @param:JsonProperty("computeEfficiency") val computeEfficiency: Double? = null,
    @param:JsonProperty("loadBalanceRatio") val loadBalanceRatio: Double? = null,
    @param:JsonProperty("avgTaskWorkMs") val avgTaskWorkMs: Double? = null,
)
