package com.nelos.parallel.adapters.config.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.nelos.parallel.commons.adapter.vo.response.EngineConfig
import com.nelos.parallel.commons.adapter.vo.response.QueueJobInfo

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class QueueStatusView @JsonCreator constructor(
    @param:JsonProperty("nodeId") val nodeId: String,
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("queueSize") val queueSize: Int? = null,
    @param:JsonProperty("activeJobs") val activeJobs: Int? = null,
    @param:JsonProperty("perfPhaseRunning") val perfPhaseRunning: Boolean? = null,
    @param:JsonProperty("perfPhasePending") val perfPhasePending: Int? = null,
    @param:JsonProperty("maxCorrectnessWorkers") val maxCorrectnessWorkers: Int? = null,
    @param:JsonProperty("jobs") val jobs: List<QueueJobInfo>? = null,
    @param:JsonProperty("engineConfig") val engineConfig: EngineConfig? = null,
)
