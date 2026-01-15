package com.nelos.parallel.commons.adapter.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Result of an adapter load/unload action on a test-runner node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class AdapterActionResult @JsonCreator constructor(
    @param:JsonProperty("adapter") val adapter: String,
    @param:JsonProperty("status") val status: String,
    @param:JsonProperty("error") val error: String? = null,
)
