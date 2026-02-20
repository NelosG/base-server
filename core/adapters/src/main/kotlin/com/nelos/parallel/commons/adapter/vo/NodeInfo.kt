package com.nelos.parallel.commons.adapter.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.response.ResourceProviderInfo
import com.nelos.parallel.commons.adapter.vo.response.TransportConfig
import com.nelos.parallel.commons.adapter.vo.response.TransportInfo
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
    @param:JsonProperty("capabilities") val capabilities: Map<String, Any?>? = null,
    @param:JsonProperty("transports") val transports: List<TransportInfo>? = null,
    @param:JsonProperty("resourceProviders") val resourceProviders: List<ResourceProviderInfo>? = null,
    @param:JsonProperty("registeredAt") val registeredAt: Instant = Instant.now()
)

fun NodeInfo.findTransport(type: TransportType): TransportInfo? =
    transports?.firstOrNull { it.type == type }

fun NodeInfo.findHttpConfig(): TransportConfig.HttpConfig? =
    findTransport(TransportType.HTTP)?.config as? TransportConfig.HttpConfig
