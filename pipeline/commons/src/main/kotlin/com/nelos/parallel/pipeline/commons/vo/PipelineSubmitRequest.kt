package com.nelos.parallel.pipeline.commons.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request from GitLab CI job to submit a test execution.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class PipelineSubmitRequest @JsonCreator constructor(
    @param:JsonProperty("projectPath") val projectPath: String? = null,
    @param:JsonProperty("mrIid") val mrIid: Long? = null,
    @param:JsonProperty("sourceBranch") val sourceBranch: String? = null,
    @param:JsonProperty("sourceRepoUrl") val sourceRepoUrl: String? = null,
    @param:JsonProperty("commitSha") val commitSha: String? = null,
    @param:JsonProperty("username") val username: String? = null,
)
