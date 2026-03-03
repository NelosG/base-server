package com.nelos.parallel.auth.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * View object for displaying an API key (without the full key).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ApiKeyView @JsonCreator constructor(
    @param:JsonProperty("id") val id: Long,
    @param:JsonProperty("keyPrefix") val keyPrefix: String,
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("active") val active: Boolean,
    @param:JsonProperty("createdAt") val createdAt: Instant,
)
