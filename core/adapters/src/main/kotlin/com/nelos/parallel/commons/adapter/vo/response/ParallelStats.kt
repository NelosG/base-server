package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * OpenMP construct counters collected during a test run.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ParallelStats @JsonCreator constructor(
    @param:JsonProperty("parallelRegions") val parallelRegions: Int? = null,
    @param:JsonProperty("tasksCreated") val tasksCreated: Int? = null,
    @param:JsonProperty("maxThreadsUsed") val maxThreadsUsed: Int? = null,
    @param:JsonProperty("singleRegions") val singleRegions: Int? = null,
    @param:JsonProperty("taskWaits") val taskWaits: Int? = null,
    @param:JsonProperty("barriers") val barriers: Int? = null,
    @param:JsonProperty("criticals") val criticals: Int? = null,
    @param:JsonProperty("forLoops") val forLoops: Int? = null,
    @param:JsonProperty("atomics") val atomics: Int? = null,
    @param:JsonProperty("sections") val sections: Int? = null,
    @param:JsonProperty("masters") val masters: Int? = null,
    @param:JsonProperty("ordered") val ordered: Int? = null,
    @param:JsonProperty("taskGroups") val taskGroups: Int? = null,
    @param:JsonProperty("simdConstructs") val simdConstructs: Int? = null,
    @param:JsonProperty("cancels") val cancels: Int? = null,
    @param:JsonProperty("flushes") val flushes: Int? = null,
    @param:JsonProperty("taskYields") val taskYields: Int? = null,
    @param:JsonProperty("workNs") val workNs: Long? = null,
    @param:JsonProperty("spanNs") val spanNs: Long? = null,
)
