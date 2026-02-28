package com.nelos.parallel.dev.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.nelos.parallel.commons.adapter.vo.response.BuildInfo
import com.nelos.parallel.commons.adapter.vo.response.ScenarioResult

/**
 * View object representing the execution status of a job.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class JobStatusView @JsonCreator constructor(
    @param:JsonProperty("jobId") val jobId: String,
    @param:JsonProperty("nodeId") val nodeId: String?,
    @param:JsonProperty("status") val status: String,
    @param:JsonProperty("error") val error: String?,
    @param:JsonProperty("durationMs") val durationMs: Long?,
    @param:JsonProperty("mode") val mode: String?,
    @param:JsonProperty("solution") val solution: String?,
    @param:JsonProperty("buildOutput") val buildOutput: String?,
    @param:JsonProperty("correctness") val correctness: List<ScenarioResult>? = null,
    @param:JsonProperty("performance") val performance: List<ScenarioResult>? = null,
    @param:JsonProperty("buildInfo") val buildInfo: BuildInfo? = null,
    @param:JsonProperty("lane") val lane: String? = null,
    @param:JsonProperty("position") val position: Int? = null,
    @param:JsonProperty("timestamp") val timestamp: String? = null,
)
