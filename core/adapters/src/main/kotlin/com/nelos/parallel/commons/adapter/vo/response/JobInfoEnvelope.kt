package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Envelope returned by the runner from `GET /api/jobs/{id}` (HTTP) and the
 * `getJobInfo` AMQP control reply. The full [TaskResult] (with correctness /
 * performance / build-info / etc.) is nested in [result] only when the job is
 * `completed`; for `queued` / `running` / `failed` states only the slim header
 * fields are populated.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class JobInfoEnvelope @JsonCreator constructor(
    @param:JsonProperty("jobId") val jobId: String,
    @param:JsonProperty("status") val status: String,
    @param:JsonProperty("solution") val solution: String? = null,
    @param:JsonProperty("position") val position: Int? = null,
    @param:JsonProperty("result") val result: TaskResult? = null,
    @param:JsonProperty("error") val error: String? = null,
) {

    /**
     * Returns the nested [TaskResult] when the runner has it (completed jobs),
     * or a minimal stub built from the envelope's header fields otherwise.
     */
    fun toTaskResult(): TaskResult = result ?: TaskResult(
        jobId = jobId,
        status = status,
        error = error,
        solution = solution,
    )
}
