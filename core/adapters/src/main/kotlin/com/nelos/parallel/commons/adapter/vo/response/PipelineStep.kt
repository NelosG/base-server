package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class PipelineStep @JsonCreator constructor(
    @param:JsonProperty("step") val step: String? = null,
    @param:JsonProperty("status") val status: String? = null,
    @param:JsonProperty("durationMs") val durationMs: Double? = null,
)
