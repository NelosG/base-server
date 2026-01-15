package com.nelos.parallel.commons.adapter.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A task to be submitted to a test-runner node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class TaskSubmission @JsonCreator constructor(
    @param:JsonProperty("jobId") val jobId: String? = null,
    @param:JsonProperty("testId") val testId: String? = null,
    @param:JsonProperty("solutionDir") val solutionDir: String? = null,
    @param:JsonProperty("solutionGitUrl") val solutionGitUrl: String? = null,
    @param:JsonProperty("testsDir") val testsDir: String? = null,
    @param:JsonProperty("testsGitUrl") val testsGitUrl: String? = null,
    @param:JsonProperty("mode") val mode: String? = "correctness",
    @param:JsonProperty("threads") val threads: Int? = null,
    @param:JsonProperty("callbackUrl") val callbackUrl: String? = null
)
