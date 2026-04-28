package com.nelos.parallel.auth.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Lightweight view of a user for admin listings.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class UserView @JsonCreator constructor(
    @param:JsonProperty("id") val id: Long? = null,
    @param:JsonProperty("login") val login: String? = null,
    @param:JsonProperty("displayName") val displayName: String? = null,
    @param:JsonProperty("type") val type: String? = null,
)
