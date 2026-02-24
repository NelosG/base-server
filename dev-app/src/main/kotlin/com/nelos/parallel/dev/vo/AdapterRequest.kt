package com.nelos.parallel.dev.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request object for loading or unloading an adapter on a node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class AdapterRequest @JsonCreator constructor(
    @param:JsonProperty("nodeId") val nodeId: String? = null,
    @param:JsonProperty("adapterName") val adapterName: String? = null,
    @param:JsonProperty("config") val config: String? = null,
)
