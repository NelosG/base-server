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
class ErrorDetails @JsonCreator constructor(
    @param:JsonProperty("step") val step: String? = null,
    @param:JsonProperty("violations") val violations: List<String>? = null,
    @param:JsonProperty("allowedPackages") val allowedPackages: List<String>? = null,
    @param:JsonProperty("allowedFrameworks") val allowedFrameworks: List<String>? = null,
    @param:JsonProperty("framework") val framework: String? = null,
    @param:JsonProperty("hasCMakeLists") val hasCMakeLists: Boolean? = null,
    @param:JsonProperty("testSourceType") val testSourceType: String? = null,
    @param:JsonProperty("solutionSourceType") val solutionSourceType: String? = null,
)
