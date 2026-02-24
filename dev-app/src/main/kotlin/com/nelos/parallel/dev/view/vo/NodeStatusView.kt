package com.nelos.parallel.dev.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * View object representing the runtime status of a test-runner node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class NodeStatusView @JsonCreator constructor(
    @param:JsonProperty("nodeId") val nodeId: String,
    @param:JsonProperty("transport") val transport: String,
    @param:JsonProperty("port") val port: Int?,
    @param:JsonProperty("capabilities") val capabilities: Map<String, Any?>,
    @param:JsonProperty("queue") val queue: Map<String, Any?>?,
)
