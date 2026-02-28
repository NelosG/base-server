package com.nelos.parallel.dev.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.nelos.parallel.commons.adapter.vo.response.ResourceProviderInfo
import com.nelos.parallel.commons.adapter.vo.response.TransportInfo

/**
 * View object representing a registered test-runner node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class NodeView @JsonCreator constructor(
    @param:JsonProperty("nodeId") val nodeId: String,
    @param:JsonProperty("capabilities") val capabilities: Map<String, Any?>,
    @param:JsonProperty("transports") val transports: List<TransportInfo>? = null,
    @param:JsonProperty("resourceProviders") val resourceProviders: List<ResourceProviderInfo>? = null,
    @param:JsonProperty("registeredAt") val registeredAt: String,
)
