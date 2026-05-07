package com.nelos.parallel.gitlab.forms.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class SubmissionDetailView @JsonCreator constructor(
    @param:JsonProperty("id") val id: Long? = null,
    @param:JsonProperty("assignmentId") val assignmentId: Long? = null,
    @param:JsonProperty("assignmentCode") val assignmentCode: String? = null,
    @param:JsonProperty("assignmentName") val assignmentName: String? = null,
    @param:JsonProperty("userId") val userId: Long? = null,
    @param:JsonProperty("login") val login: String? = null,
    @param:JsonProperty("displayName") val displayName: String? = null,
    @param:JsonProperty("gitlabName") val gitlabName: String? = null,
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("mrIid") val mrIid: Long? = null,
    @param:JsonProperty("mrUrl") val mrUrl: String? = null,
    @param:JsonProperty("sourceBranch") val sourceBranch: String? = null,
    @param:JsonProperty("solutionRepoUrl") val solutionRepoUrl: String? = null,
    @param:JsonProperty("commitSha") val commitSha: String? = null,
    @param:JsonProperty("createdAt") val createdAt: String? = null,
    @param:JsonProperty("completedAt") val completedAt: String? = null,
    @param:JsonProperty("resultSummary") val resultSummary: String? = null,
    @param:JsonProperty("logText") val logText: String? = null,
    @param:JsonProperty("resultJson") val resultJson: String? = null,
)
