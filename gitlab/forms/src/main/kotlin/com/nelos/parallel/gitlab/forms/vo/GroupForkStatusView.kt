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
class GroupForkStatusView @JsonCreator constructor(
    @param:JsonProperty("id") val id: Long? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("memberCount") val memberCount: Int? = null,
    @param:JsonProperty("missingForkCount") val missingForkCount: Int? = null,
    @param:JsonProperty("unavailableCount") val unavailableCount: Int? = null,
    @param:JsonProperty("members") val members: List<MemberForkStatus>? = null,
)

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class MemberForkStatus @JsonCreator constructor(
    @param:JsonProperty("username") val username: String? = null,
    @param:JsonProperty("displayName") val displayName: String? = null,
    @param:JsonProperty("hasFork") val hasFork: Boolean? = null,
    @param:JsonProperty("gitlabExists") val gitlabExists: Boolean? = null,
)
