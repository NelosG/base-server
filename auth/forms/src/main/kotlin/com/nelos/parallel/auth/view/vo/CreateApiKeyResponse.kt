package com.nelos.parallel.auth.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response after creating a new API key, containing the raw key shown once.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class CreateApiKeyResponse @JsonCreator constructor(
    @param:JsonProperty("id") val id: Long,
    @param:JsonProperty("keyPrefix") val keyPrefix: String,
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("rawKey") val rawKey: String,
)
