package com.nelos.parallel.auth.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * User view object containing the login and assigned roles.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class UserDetailedView @JsonCreator constructor(
    @param:JsonProperty("login") val login: String,
    @param:JsonProperty("roles") val roles: List<String>,
)