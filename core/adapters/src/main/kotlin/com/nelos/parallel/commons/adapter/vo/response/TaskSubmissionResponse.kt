package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response from a node after accepting a task submission.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class TaskSubmissionResponse @JsonCreator constructor(
    @param:JsonProperty("jobId") val jobId: String,
    @param:JsonProperty("status") val status: String,
    @param:JsonProperty("position") val position: Int? = null,
    @param:JsonProperty("nodeId") val nodeId: String? = null,
    @param:JsonProperty("mode") val mode: String? = null,
    @param:JsonProperty("solution") val solution: String? = null,
    @param:JsonProperty("memoryLimitMb") val memoryLimitMb: Long? = null,
    @param:JsonProperty("timestamp") val timestamp: String? = null,
)
