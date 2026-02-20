package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Memory usage statistics for a single test run.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class MemoryStats @JsonCreator constructor(
    @param:JsonProperty("peakMemoryBytes") val peakMemoryBytes: Long? = null,
    @param:JsonProperty("allocations") val allocations: Long? = null,
    @param:JsonProperty("deallocations") val deallocations: Long? = null,
    @param:JsonProperty("limitExceeded") val limitExceeded: Boolean? = null,
)
