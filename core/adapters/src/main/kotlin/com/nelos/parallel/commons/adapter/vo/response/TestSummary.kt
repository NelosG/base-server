package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class TestSummary @JsonCreator constructor(
    @param:JsonProperty("totalTests") val totalTests: Int? = null,
    @param:JsonProperty("passed") val passed: Int? = null,
    @param:JsonProperty("failed") val failed: Int? = null,
    @param:JsonProperty("failedByTimeout") val failedByTimeout: Int? = null,
    @param:JsonProperty("failedByOom") val failedByOom: Int? = null,
    @param:JsonProperty("failedByCrash") val failedByCrash: Int? = null,
    @param:JsonProperty("failedByCorrectness") val failedByCorrectness: Int? = null,
    @param:JsonProperty("maxTimeMs") val maxTimeMs: Double? = null,
    @param:JsonProperty("maxRssKb") val maxRssKb: Long? = null,
    @param:JsonProperty("maxCgMemPeakKb") val maxCgMemPeakKb: Long? = null,
    @param:JsonProperty("totalCpuTimeSec") val totalCpuTimeSec: Double? = null,
    @param:JsonProperty("scalability") val scalability: List<ScalabilityPoint>? = null,
)

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ScalabilityPoint @JsonCreator constructor(
    @param:JsonProperty("threads") val threads: Int? = null,
    @param:JsonProperty("totalTimeMs") val totalTimeMs: Double? = null,
    // Engine pairwise-gates speedup/efficiency: only tests that passed on BOTH
    // T=1 baseline AND this thread count contribute. testsCompared / testsSkipped
    // reflect that gating. An entry with testsCompared == 0 is omitted entirely.
    @param:JsonProperty("speedup") val speedup: Double? = null,
    @param:JsonProperty("efficiency") val efficiency: Double? = null,
    @param:JsonProperty("maxRssKb") val maxRssKb: Long? = null,
    @param:JsonProperty("totalCpuTimeSec") val totalCpuTimeSec: Double? = null,
    @param:JsonProperty("testsCompared") val testsCompared: Int? = null,
    @param:JsonProperty("testsSkipped") val testsSkipped: Int? = null,
)
