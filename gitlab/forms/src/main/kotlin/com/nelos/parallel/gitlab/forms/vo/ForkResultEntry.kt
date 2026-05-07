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
class ForkResultEntry @JsonCreator constructor(
    @param:JsonProperty("username") val username: String? = null,
    @param:JsonProperty("success") val success: Boolean? = null,
    @param:JsonProperty("forkUrl") val forkUrl: String? = null,
    @param:JsonProperty("error") val error: String? = null,
)
