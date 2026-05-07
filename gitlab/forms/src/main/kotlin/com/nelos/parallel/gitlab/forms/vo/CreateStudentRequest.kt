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
class CreateStudentRequest @JsonCreator constructor(
    @param:JsonProperty("login") val login: String? = null,
    @param:JsonProperty("displayName") val displayName: String? = null,
    @param:JsonProperty("gitlabName") val gitlabName: String? = null,
    @param:JsonProperty("groupId") val groupId: Long? = null,
)
