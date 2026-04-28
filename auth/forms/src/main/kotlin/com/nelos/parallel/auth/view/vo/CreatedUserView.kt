package com.nelos.parallel.auth.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Returned once after a user is created (or has their password reset). Contains the generated
 * one-time password in plain text - admin must capture it and forward to the user.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class CreatedUserView @JsonCreator constructor(
    @param:JsonProperty("id") val id: Long,
    @param:JsonProperty("login") val login: String,
    @param:JsonProperty("password") val password: String,
)
