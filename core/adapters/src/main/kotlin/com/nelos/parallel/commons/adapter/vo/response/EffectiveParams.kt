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
class EffectiveParams @JsonCreator constructor(
    @param:JsonProperty("mode") val mode: String? = null,
    @param:JsonProperty("threads") val threads: Int? = null,
    @param:JsonProperty("memoryLimitMb") val memoryLimitMb: Long? = null,
    @param:JsonProperty("wallTimeSec") val wallTimeSec: Int? = null,
    @param:JsonProperty("cpuTimeSec") val cpuTimeSec: Int? = null,
    @param:JsonProperty("maxProcesses") val maxProcesses: Int? = null,
    @param:JsonProperty("warmupIterations") val warmupIterations: Int? = null,
    @param:JsonProperty("testSourceType") val testSourceType: String? = null,
    @param:JsonProperty("solutionSourceType") val solutionSourceType: String? = null,
    @param:JsonProperty("hasCMakeLists") val hasCMakeLists: Boolean? = null,
)
