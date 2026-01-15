package com.nelos.parallel.commons.adapter.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.nelos.parallel.commons.adapter.enums.TransportType

/**
 * Request from a test-runner node to register or deregister with the orchestrator.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class NodeRegistrationRequest @JsonCreator constructor(
    @param:JsonProperty("type") val type: String? = DEFAULT_TYPE,
    @param:JsonProperty("nodeId") val nodeId: String,
    @param:JsonProperty("transport") val transport: TransportType? = null,
    @param:JsonProperty("host") val host: String? = null,
    @param:JsonProperty("port") val port: Int = 0,
    @param:JsonProperty("authToken") val authToken: String? = null,
    @param:JsonProperty("capabilities") val capabilities: Map<String, Any?>? = null,
    @param:JsonProperty("timestamp") val timestamp: String? = null,
    @param:JsonProperty("currentLoad") val currentLoad: Map<String, Any?>? = null
) {

    companion object {
        const val TYPE_REGISTER = "register"
        const val TYPE_DEREGISTER = "deregister"
        const val DEFAULT_TYPE = TYPE_REGISTER
    }
}
