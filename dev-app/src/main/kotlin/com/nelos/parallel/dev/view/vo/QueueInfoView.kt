package com.nelos.parallel.dev.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * View object representing a single task queue info.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class QueueInfoView @JsonCreator constructor(
    @param:JsonProperty("queued") val queued: Int,
    @param:JsonProperty("running") val running: Int,
    @param:JsonProperty("totalWorkers") val totalWorkers: Int?,
)
