package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Typed transport configuration — discriminated by [TransportType] during deserialization.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
sealed class TransportConfig {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class HttpConfig @JsonCreator constructor(
        @param:JsonProperty("port") val port: Int? = null,
        @param:JsonProperty("host") val host: String? = null,
        @param:JsonProperty("authToken") val authToken: String? = null,
    ) : TransportConfig()

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    class AmqpConfig @JsonCreator constructor(
        @param:JsonProperty("host") val host: String? = null,
        @param:JsonProperty("port") val port: Int? = null,
        @param:JsonProperty("vhost") val vhost: String? = null,
    ) : TransportConfig()
}
