package com.nelos.parallel.dev.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * View object representing the result of a resource provider load/unload action.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ResourceProviderActionView @JsonCreator constructor(
    @param:JsonProperty("provider") val provider: String,
    @param:JsonProperty("status") val status: String,
    @param:JsonProperty("error") val error: String?,
)
