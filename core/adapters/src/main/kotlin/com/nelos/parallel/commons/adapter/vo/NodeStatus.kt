package com.nelos.parallel.commons.adapter.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.nelos.parallel.commons.adapter.enums.TransportType

/**
 * Runtime status of a test-runner node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class NodeStatus @JsonCreator constructor(
    @param:JsonProperty("nodeId") val nodeId: String,
    @param:JsonProperty("transport") val transport: TransportType? = null,
    @param:JsonProperty("port") val port: Int? = null,
    @param:JsonProperty("capabilities") val capabilities: Map<String, Any?>? = null,
    @param:JsonProperty("activeJobs") val activeJobs: Int? = null,
    @param:JsonProperty("queuedJobs") val queuedJobs: Int? = null,
    @param:JsonProperty("queue") val queue: Map<String, Any?>? = null,
    @param:JsonProperty("currentLoad") val currentLoad: Map<String, Any?>? = null,
    @param:JsonProperty("timestamp") val timestamp: String? = null
) {
    fun effectiveQueue(): Map<String, Any?>? = queue ?: currentLoad
}
