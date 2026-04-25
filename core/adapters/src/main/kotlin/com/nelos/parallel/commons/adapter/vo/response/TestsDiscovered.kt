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
class TestsDiscovered @JsonCreator constructor(
    @param:JsonProperty("correctnessScenarios") val correctnessScenarios: Int? = null,
    @param:JsonProperty("correctnessTests") val correctnessTests: Int? = null,
    @param:JsonProperty("performanceScenarios") val performanceScenarios: Int? = null,
    @param:JsonProperty("performanceTests") val performanceTests: Int? = null,
    @param:JsonProperty("pluginsLoaded") val pluginsLoaded: List<PluginInfo>? = null,
)

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class PluginInfo @JsonCreator constructor(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("status") val status: String? = null,
)
