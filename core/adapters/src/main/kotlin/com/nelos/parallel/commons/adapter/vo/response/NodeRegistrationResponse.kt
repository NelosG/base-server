package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Response from the orchestrator after a node registration request.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class NodeRegistrationResponse @JsonCreator constructor(
    @param:JsonProperty("status") val status: String,
    @param:JsonProperty("nodeId") val nodeId: String,
    @param:JsonProperty("timestamp") val timestamp: Instant = Instant.now()
)
