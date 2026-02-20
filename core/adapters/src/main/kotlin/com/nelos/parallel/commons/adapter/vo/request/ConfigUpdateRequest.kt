package com.nelos.parallel.commons.adapter.vo.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request for updating dynamic configuration on a test-runner node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ConfigUpdateRequest @JsonCreator constructor(
    @param:JsonProperty("maxCorrectnessWorkers") val maxCorrectnessWorkers: Int? = null,
    @param:JsonProperty("jobRetentionSeconds") val jobRetentionSeconds: Int? = null,
    @param:JsonProperty("defaultMemoryLimitMb") val defaultMemoryLimitMb: Long? = null,
)
