package com.nelos.parallel.dev.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request object for updating dynamic configuration on a node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ConfigRequest @JsonCreator constructor(
    @param:JsonProperty("nodeId") val nodeId: String? = null,
    @param:JsonProperty("maxCorrectnessWorkers") val maxCorrectnessWorkers: Int? = null,
    @param:JsonProperty("jobRetentionSeconds") val jobRetentionSeconds: Int? = null,
    @param:JsonProperty("defaultMemoryLimitMb") val defaultMemoryLimitMb: Long? = null,
)
