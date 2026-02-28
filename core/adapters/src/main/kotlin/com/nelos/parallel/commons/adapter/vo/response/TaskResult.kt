package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Result returned by a test-runner node after executing a task.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class TaskResult @JsonCreator constructor(
    @param:JsonProperty("jobId") val jobId: String,
    @param:JsonProperty("nodeId") val nodeId: String? = null,
    @param:JsonProperty("status") val status: String,
    @param:JsonProperty("error") val error: String? = null,
    @param:JsonProperty("durationMs") val durationMs: Long? = null,
    @param:JsonProperty("timestamp") val timestamp: String? = null,
    @param:JsonProperty("mode") val mode: String? = null,
    @param:JsonProperty("solution") val solution: String? = null,
    @param:JsonProperty("buildOutput") val buildOutput: String? = null,
    @param:JsonProperty("correctness") val correctness: List<ScenarioResult>? = null,
    @param:JsonProperty("performance") val performance: List<ScenarioResult>? = null,
    @param:JsonProperty("buildInfo") val buildInfo: BuildInfo? = null,
    @param:JsonProperty("lane") val lane: String? = null,
    @param:JsonProperty("position") val position: Int? = null,
)
