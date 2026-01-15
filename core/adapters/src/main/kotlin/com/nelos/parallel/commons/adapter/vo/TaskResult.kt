package com.nelos.parallel.commons.adapter.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

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
    @param:JsonProperty("result") val result: Any? = null,
    @param:JsonProperty("error") val error: String? = null,
    @param:JsonProperty("durationMs") val durationMs: Long? = 0,
    @param:JsonProperty("timestamp") val timestamp: Instant? = null,
    @param:JsonProperty("mode") val mode: String? = null,
    @param:JsonProperty("buildOutput") val buildOutput: String? = null
)
