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
class CreateForksRequest @JsonCreator constructor(
    @param:JsonProperty("assignmentId") val assignmentId: Long? = null,
    @param:JsonProperty("groupId") val groupId: Long? = null,
    @param:JsonProperty("groupIds") val groupIds: List<Long>? = null,
    @param:JsonProperty("usernames") val usernames: List<String>? = null,
)
