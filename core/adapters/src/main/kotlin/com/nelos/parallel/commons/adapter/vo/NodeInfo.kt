package com.nelos.parallel.commons.adapter.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.nelos.parallel.commons.adapter.enums.TransportType
import java.time.Instant

/**
 * Information about a registered test-runner node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class NodeInfo @JsonCreator constructor(
    @param:JsonProperty("nodeId") val nodeId: String,
    @param:JsonProperty("transport") val transport: TransportType,
    @param:JsonProperty("host") val host: String,
    @param:JsonProperty("port") val port: Int,
    @param:JsonProperty("authToken") val authToken: String?,
    @param:JsonProperty("capabilities") val capabilities: Map<String, Any?>? = null,
    @param:JsonProperty("registeredAt") val registeredAt: Instant = Instant.now()
)
