package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Flat snapshot of a test-runner node's [JobQueue] state.
 *
 * Sent inline by `GET /api/status`, in `info` events under `currentLoad`, and
 * as the body of `queueStatusResponse` / `updateConfigResponse` over RabbitMQ.
 *
 * Phase-level sandbox exclusivity: a job's correctness phase can run alongside
 * other correctness phases; a perf phase grabs the sandbox alone and blocks
 * further correctness phase entries until done.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class QueueStatus @JsonCreator constructor(
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("queueSize") val queueSize: Int? = null,
    @param:JsonProperty("activeJobs") val activeJobs: Int? = null,
    @param:JsonProperty("maxCorrectnessWorkers") val maxCorrectnessWorkers: Int? = null,
    @param:JsonProperty("perfPhaseRunning") val perfPhaseRunning: Boolean? = null,
    @param:JsonProperty("perfPhasePending") val perfPhasePending: Int? = null,
    @param:JsonProperty("jobs") val jobs: List<QueueJobInfo>? = null,
)
