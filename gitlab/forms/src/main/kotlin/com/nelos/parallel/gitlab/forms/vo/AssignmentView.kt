package com.nelos.parallel.gitlab.forms.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.nelos.parallel.pipeline.commons.service.EvaluatorScript

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class AssignmentView @JsonCreator constructor(
    @param:JsonProperty("id") val id: Long? = null,
    @param:JsonProperty("code") val code: String? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("description") val description: String? = null,
    @param:JsonProperty("gitlabProjectPath") val gitlabProjectPath: String? = null,
    @param:JsonProperty("testRepoUrl") val testRepoUrl: String? = null,
    @param:JsonProperty("testRepoBranch") val testRepoBranch: String? = null,
    @param:JsonProperty("memoryLimitMb") val memoryLimitMb: Long? = null,
    @param:JsonProperty("threads") val threads: Int? = null,
    @param:JsonProperty("wallTimeSec") val wallTimeSec: Int? = null,
    @param:JsonProperty("cpuTimeSec") val cpuTimeSec: Int? = null,
    @param:JsonProperty("maxProcesses") val maxProcesses: Int? = null,
    @param:JsonProperty("active") val active: Boolean? = null,
    @param:JsonProperty("evaluatorScript") val evaluatorScript: EvaluatorScript? = null,
)
