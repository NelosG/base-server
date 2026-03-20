package com.nelos.parallel.dev.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request object for submitting a test task to a node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class TaskSubmitRequest @JsonCreator constructor(
    @param:JsonProperty("nodeId") val nodeId: String? = null,
    @param:JsonProperty("jobId") val jobId: String? = null,
    @param:JsonProperty("testId") val testId: String? = null,
    @param:JsonProperty("solutionSourceType") val solutionSourceType: String? = null,
    @param:JsonProperty("solutionUrl") val solutionUrl: String? = null,
    @param:JsonProperty("solutionBranch") val solutionBranch: String? = null,
    @param:JsonProperty("solutionToken") val solutionToken: String? = null,
    @param:JsonProperty("solutionPath") val solutionPath: String? = null,
    @param:JsonProperty("testSourceType") val testSourceType: String? = null,
    @param:JsonProperty("testUrl") val testUrl: String? = null,
    @param:JsonProperty("testBranch") val testBranch: String? = null,
    @param:JsonProperty("testToken") val testToken: String? = null,
    @param:JsonProperty("testPath") val testPath: String? = null,
    @param:JsonProperty("mode") val mode: String? = "correctness",
    @param:JsonProperty("threads") val threads: Int? = null,
    @param:JsonProperty("numaNode") val numaNode: Int? = null,
    @param:JsonProperty("memoryLimitMb") val memoryLimitMb: Long? = null,
)
