package com.nelos.parallel.auth.vo

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
class ChangePasswordData @JsonCreator constructor(
    @param:JsonProperty("currentPassword") val currentPassword: String? = null,
    @param:JsonProperty("newPassword") val newPassword: String? = null,
)
