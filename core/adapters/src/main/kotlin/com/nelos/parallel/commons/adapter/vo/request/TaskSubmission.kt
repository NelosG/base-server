package com.nelos.parallel.commons.adapter.vo.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.nelos.parallel.commons.adapter.enums.SourceType
import com.nelos.parallel.commons.adapter.enums.TestMode

/**
 * A task to be submitted to a test-runner node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class TaskSubmission @JsonCreator constructor(
    @param:JsonProperty("jobId") val jobId: String? = null,
    @param:JsonProperty("testId") val testId: String? = null,
    @param:JsonProperty("solutionSourceType") val solutionSourceType: SourceType? = null,
    @param:JsonProperty("solutionSource") val solutionSource: SourceDescriptor? = null,
    @param:JsonProperty("testSourceType") val testSourceType: SourceType? = null,
    @param:JsonProperty("testSource") val testSource: SourceDescriptor? = null,
    @param:JsonProperty("mode") val mode: TestMode? = TestMode.CORRECTNESS,
    @param:JsonProperty("threads") val threads: Int? = null,
    @param:JsonProperty("numaNode") val numaNode: Int? = null,
    @param:JsonProperty("callbackUrl") val callbackUrl: String? = null,
    @param:JsonProperty("memoryLimitMb") val memoryLimitMb: Long? = null
)
