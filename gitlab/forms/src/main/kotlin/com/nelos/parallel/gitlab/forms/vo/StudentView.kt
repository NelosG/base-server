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
class StudentView @JsonCreator constructor(
    @param:JsonProperty("id") val id: Long? = null,
    @param:JsonProperty("login") val login: String? = null,
    @param:JsonProperty("displayName") val displayName: String? = null,
    @param:JsonProperty("gitlabName") val gitlabName: String? = null,
    @param:JsonProperty("groupIds") val groupIds: List<Long>? = null,
    @param:JsonProperty("groupNames") val groupNames: List<String>? = null,
    @param:JsonProperty("submissionCount") val submissionCount: Int? = null,
    @param:JsonProperty("passwordStatus") val passwordStatus: String? = null,
    @param:JsonProperty("initialPassword") val initialPassword: String? = null,
)
