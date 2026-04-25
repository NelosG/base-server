package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Assignment configuration returned by the test-runner as part of TaskResult.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class AssignmentConfig @JsonCreator constructor(
    @param:JsonProperty("mode") val mode: String? = null,
    @param:JsonProperty("framework") val framework: String? = null,
    @param:JsonProperty("correctnessMode") val correctnessMode: String? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("allowedFrameworks") val allowedFrameworks: List<String>? = null,
    @param:JsonProperty("allowedPackages") val allowedPackages: List<String>? = null,
)
