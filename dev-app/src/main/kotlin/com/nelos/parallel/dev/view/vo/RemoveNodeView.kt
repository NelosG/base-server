package com.nelos.parallel.dev.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * View object representing the result of a node deregistration.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class RemoveNodeView @JsonCreator constructor(
    @param:JsonProperty("nodeId") val nodeId: String,
    @param:JsonProperty("removed") val removed: Boolean,
)
