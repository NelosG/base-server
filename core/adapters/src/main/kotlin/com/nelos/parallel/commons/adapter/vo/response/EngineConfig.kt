package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Engine-side per-job defaults and tuning knobs.
 *
 * Emitted in the `engineConfig` block of `info` node events. Mirrors the
 * fields accepted by PUT `/api/config` / RabbitMQ `updateConfig` so the
 * orchestrator can read back what the engine is actually using after any
 * `server.json` overrides or runtime updates.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class EngineConfig @JsonCreator constructor(
    @param:JsonProperty("maxCorrectnessWorkers") val maxCorrectnessWorkers: Int? = null,
    @param:JsonProperty("jobRetentionSeconds") val jobRetentionSeconds: Int? = null,
    @param:JsonProperty("defaultMemoryLimitMb") val defaultMemoryLimitMb: Long? = null,
    @param:JsonProperty("defaultThreads") val defaultThreads: Int? = null,
    @param:JsonProperty("defaultWallTimeSec") val defaultWallTimeSec: Int? = null,
    @param:JsonProperty("defaultCpuTimeSec") val defaultCpuTimeSec: Int? = null,
    @param:JsonProperty("sandboxProcessMultiplier") val sandboxProcessMultiplier: Int? = null,
)
