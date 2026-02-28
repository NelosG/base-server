package com.nelos.parallel.dev.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.node.ObjectNode
import com.nelos.parallel.commons.adapter.enums.AdapterStatus

/**
 * View object representing a resource provider on a test-runner node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ResourceProviderView @JsonCreator constructor(
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("status") val status: AdapterStatus?,
    @param:JsonProperty("dllPath") val dllPath: String?,
    @param:JsonProperty("config") val config: ObjectNode? = null,
)
