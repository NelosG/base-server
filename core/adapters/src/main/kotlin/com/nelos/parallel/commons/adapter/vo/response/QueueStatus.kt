package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Overview of all task queues on a test-runner node.
 *
 * Supports both structured format (with nested [QueueInfo]) and
 * flat format returned by C-tests-runner `/api/status` endpoint.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class QueueStatus @JsonCreator constructor(
    @param:JsonProperty("correctnessQueue") val correctnessQueue: QueueInfo? = null,
    @param:JsonProperty("performanceQueue") val performanceQueue: QueueInfo? = null,
    @param:JsonProperty("correctnessQueueSize") private val correctnessQueueSize: Int? = null,
    @param:JsonProperty("activeCorrectness") private val activeCorrectness: Int? = null,
    @param:JsonProperty("performanceQueueSize") private val performanceQueueSize: Int? = null,
    @param:JsonProperty("perfRunning") private val perfRunning: Boolean? = null,
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("maxCorrectnessWorkers") val maxCorrectnessWorkers: Int? = null,
    @param:JsonProperty("maxOmpThreads") val maxOmpThreads: Int? = null,
    @param:JsonProperty("currentPerfJob") val currentPerfJob: String? = null,
    @param:JsonProperty("jobRetentionSeconds") val jobRetentionSeconds: Int? = null,
    @param:JsonProperty("defaultMemoryLimitMb") val defaultMemoryLimitMb: Long? = null,
) {

    fun effectiveCorrectnessQueue(): QueueInfo? =
        correctnessQueue ?: correctnessQueueSize?.let {
            QueueInfo(queued = it, running = activeCorrectness ?: 0)
        }

    fun effectivePerformanceQueue(): QueueInfo? =
        performanceQueue ?: performanceQueueSize?.let {
            QueueInfo(queued = it, running = if (perfRunning == true) 1 else 0)
        }
}
