package com.nelos.parallel.dev.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * View object representing queue status overview for a node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class QueueStatusView @JsonCreator constructor(
    @param:JsonProperty("nodeId") val nodeId: String,
    @param:JsonProperty("correctnessQueue") val correctnessQueue: QueueInfoView?,
    @param:JsonProperty("performanceQueue") val performanceQueue: QueueInfoView?,
)
