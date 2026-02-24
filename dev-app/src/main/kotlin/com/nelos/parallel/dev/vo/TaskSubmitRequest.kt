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
    @param:JsonProperty("solutionGitUrl") val solutionGitUrl: String? = null,
    @param:JsonProperty("solutionDir") val solutionDir: String? = null,
    @param:JsonProperty("testsGitUrl") val testsGitUrl: String? = null,
    @param:JsonProperty("testsDir") val testsDir: String? = null,
    @param:JsonProperty("mode") val mode: String? = "correctness",
    @param:JsonProperty("threads") val threads: Int? = null,
)
