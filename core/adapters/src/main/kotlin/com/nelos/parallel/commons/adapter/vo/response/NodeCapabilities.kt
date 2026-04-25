package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Capabilities advertised by a test-runner node in its `online` / `info` events.
 *
 * `maxThreads` is the upper bound the engine accepts for the `threads` field
 * of any submission (currently `hardware_concurrency * 2`).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class NodeCapabilities @JsonCreator constructor(
    @param:JsonProperty("maxConcurrentCorrectness") val maxConcurrentCorrectness: Int? = null,
    @param:JsonProperty("maxThreads") val maxThreads: Int? = null,
)
