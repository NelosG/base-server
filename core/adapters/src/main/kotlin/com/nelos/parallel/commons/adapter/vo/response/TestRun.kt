package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Result of a single test execution with a specific thread count.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class TestRun @JsonCreator constructor(
    @param:JsonProperty("threads") val threads: Int,
    @param:JsonProperty("passed") val passed: Boolean,
    @param:JsonProperty("message") val message: String? = null,
    @param:JsonProperty("stats") val stats: RunStats? = null,
    @param:JsonProperty("parallelStats") val parallelStats: ParallelStats? = null,
    @param:JsonProperty("memoryStats") val memoryStats: MemoryStats? = null,
)
