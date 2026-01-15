package com.nelos.parallel.commons.adapter.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Information about a single task queue on a test-runner node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class QueueInfo @JsonCreator constructor(
    @param:JsonProperty("queued") val queued: Int = 0,
    @param:JsonProperty("running") val running: Int = 0,
    @param:JsonProperty("totalWorkers") val totalWorkers: Int? = null,
)
