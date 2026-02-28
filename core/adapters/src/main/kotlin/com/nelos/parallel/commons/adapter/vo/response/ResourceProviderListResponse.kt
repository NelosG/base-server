package com.nelos.parallel.commons.adapter.vo.response

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Wrapper for resource provider list responses from test-runner nodes.
 * Runner wraps provider lists in `{"providers": [...]}` object.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class ResourceProviderListResponse @JsonCreator constructor(
    @param:JsonProperty("providers") val providers: List<ResourceProviderInfo> = emptyList(),
)
