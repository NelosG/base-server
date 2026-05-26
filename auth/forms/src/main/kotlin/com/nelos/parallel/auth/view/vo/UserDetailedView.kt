package com.nelos.parallel.auth.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * User view object containing the login, display name, and assigned roles. The
 * `passwordChangeRequired` flag tells the front-end to surface the OTP-change
 * modal. `features` is a set of feature-flag tokens used by the navbar to
 * hide nav entries whose backing module isn't on the classpath - e.g.
 * "adapter-http-forms" is present only when the HTTP adapter forms jar is
 * deployed.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class UserDetailedView @JsonCreator constructor(
    @param:JsonProperty("login") val login: String,
    @param:JsonProperty("displayName") val displayName: String? = null,
    @param:JsonProperty("roles") val roles: List<String>,
    @param:JsonProperty("passwordChangeRequired") val passwordChangeRequired: Boolean = false,
    @param:JsonProperty("features") val features: Set<String> = emptySet(),
)
