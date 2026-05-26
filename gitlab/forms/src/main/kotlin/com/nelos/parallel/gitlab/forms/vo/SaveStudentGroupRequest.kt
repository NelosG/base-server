package com.nelos.parallel.gitlab.forms.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Full-replace payload for `saveGroup`. PUT semantics: `name` is required,
 * `description` and `members` are nullable - `null` clears them.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class SaveStudentGroupRequest @JsonCreator constructor(
    @param:JsonProperty("id") val id: Long? = null,
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("description") val description: String? = null,
    @param:JsonProperty("members") val members: List<StudentGroupMemberView>? = null,
)
