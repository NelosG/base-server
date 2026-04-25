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
    @param:JsonProperty("durationMs") val durationMs: Double? = null,
    @param:JsonProperty("timestamp") val timestamp: String? = null,
    @param:JsonProperty("mode") val mode: String? = null,
    @param:JsonProperty("solution") val solution: String? = null,
    @param:JsonProperty("buildOutput") val buildOutput: String? = null,
    @param:JsonProperty("correctness") val correctness: List<ScenarioResult>? = null,
    @param:JsonProperty("performance") val performance: List<ScenarioResult>? = null,
    @param:JsonProperty("buildInfo") val buildInfo: BuildInfo? = null,
    @param:JsonProperty("threadCounts") val threadCounts: ThreadCounts? = null,
    @param:JsonProperty("totalTimeMs") val totalTimeMs: Double? = null,
    @param:JsonProperty("assignmentConfig") val assignmentConfig: AssignmentConfig? = null,
    @param:JsonProperty("pipeline") val pipeline: List<PipelineStep>? = null,
    @param:JsonProperty("effectiveParams") val effectiveParams: EffectiveParams? = null,
    @param:JsonProperty("environment") val environment: EnvironmentInfo? = null,
    @param:JsonProperty("failedStep") val failedStep: String? = null,
    @param:JsonProperty("errorDetails") val errorDetails: ErrorDetails? = null,
    @param:JsonProperty("testsDiscovered") val testsDiscovered: TestsDiscovered? = null,
    @param:JsonProperty("summary") val summary: ResultSummary? = null,
    @param:JsonProperty("performanceSkipped") val performanceSkipped: Boolean? = null,
    @param:JsonProperty("performanceSkipReason") val performanceSkipReason: String? = null,
)
