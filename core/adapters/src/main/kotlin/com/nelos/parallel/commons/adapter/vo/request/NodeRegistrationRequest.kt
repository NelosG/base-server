package com.nelos.parallel.commons.adapter.vo.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.nelos.parallel.commons.adapter.enums.NodeEventType
import com.nelos.parallel.commons.adapter.vo.response.ResourceProviderInfo
import com.nelos.parallel.commons.adapter.vo.response.TransportInfo

/**
 * Request from a test-runner node to register or deregister with the orchestrator.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class NodeRegistrationRequest @JsonCreator constructor(
    @param:JsonProperty("type") val type: NodeEventType? = NodeEventType.ONLINE,
    @param:JsonProperty("nodeId") val nodeId: String,
    @param:JsonProperty("capabilities") val capabilities: Map<String, Any?>? = null,
    @param:JsonProperty("transports") val transports: List<TransportInfo>? = null,
    @param:JsonProperty("resourceProviders") val resourceProviders: List<ResourceProviderInfo>? = null,
    @param:JsonProperty("timestamp") val timestamp: String? = null,
    @param:JsonProperty("currentLoad") val currentLoad: Map<String, Any?>? = null
)
