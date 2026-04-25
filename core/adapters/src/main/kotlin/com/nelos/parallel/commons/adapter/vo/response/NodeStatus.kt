package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Runtime status of a test-runner node.
 *
 * The `type` field is intentionally a free-form [String] rather than [com.nelos.parallel.commons.adapter.enums.NodeEventType]:
 * the runner sends `"info"` for direct status queries (HTTP `/api/node/status`),
 * but rewrites it to `"statusResponse"` (or any `<command>Response` form) when
 * the same payload is published as an AMQP control reply. We accept both shapes
 * since the orchestrator uses the field only for diagnostics.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class NodeStatus @JsonCreator constructor(
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("nodeId") val nodeId: String,
    @param:JsonProperty("capabilities") val capabilities: NodeCapabilities? = null,
    @param:JsonProperty("currentLoad") val currentLoad: QueueStatus? = null,
    @param:JsonProperty("engineConfig") val engineConfig: EngineConfig? = null,
    @param:JsonProperty("transports") val transports: List<TransportInfo>? = null,
    @param:JsonProperty("resourceProviders") val resourceProviders: List<ResourceProviderInfo>? = null,
    @param:JsonProperty("timestamp") val timestamp: String? = null,
)
