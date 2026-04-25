package com.nelos.parallel.commons.adapter.vo.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Incremental progress event published by a runner during job execution. Two shapes
 * exist on the wire and both are deserialized into this single VO:
 *
 * - **Phase-level** events use [phase] = `received|resolveTests|...|runPerformance` and
 *   may carry a [message] and a 0..1 [progress] indicator (only meaningful for test
 *   phases).
 * - **Per-test** events use [phase] = `"test"` plus [scenario], [test], [threadCount],
 *   [status] = `"running"|"passed"|"failed"`, optional [timeMs] and [message].
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ProgressEvent @JsonCreator constructor(
    @param:JsonProperty("jobId") val jobId: String,
    @param:JsonProperty("nodeId") val nodeId: String? = null,
    @param:JsonProperty("phase") val phase: String,
    @param:JsonProperty("message") val message: String? = null,
    @param:JsonProperty("progress") val progress: Double? = null,
    @param:JsonProperty("timestamp") val timestamp: String? = null,
    @param:JsonProperty("scenario") val scenario: String? = null,
    @param:JsonProperty("test") val test: String? = null,
    @param:JsonProperty("threadCount") val threadCount: Int? = null,
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("timeMs") val timeMs: Double? = null,
)
