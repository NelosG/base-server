package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Process-level statistics from a test execution (cgroup metrics, exit code, timeouts).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ProcessStats @JsonCreator constructor(
    @param:JsonProperty("exitCode") val exitCode: Int? = null,
    @param:JsonProperty("cgMemPeakKb") val cgMemPeakKb: Long? = null,
    @param:JsonProperty("maxRssKb") val maxRssKb: Long? = null,
    @param:JsonProperty("cpuTimeSec") val cpuTimeSec: Double? = null,
    @param:JsonProperty("wallTimeSec") val wallTimeSec: Double? = null,
    @param:JsonProperty("oomKilled") val oomKilled: Boolean? = null,
    @param:JsonProperty("timedOut") val timedOut: Boolean? = null,
)
