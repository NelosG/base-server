package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Wrapper for adapter list responses from test-runner nodes.
 * Runner wraps adapter lists in `{"adapters": [...]}` object.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class AdapterListResponse @JsonCreator constructor(
    @param:JsonProperty("adapters") val adapters: List<AdapterInfo> = emptyList(),
)
