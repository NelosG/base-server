package com.nelos.parallel.commons.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Incoming request for the ViewEngine, specifying the target service, method, and arguments.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ViewRequest @JsonCreator constructor(
    @param:JsonProperty("service") val service: String,
    @param:JsonProperty("method") val method: String,
    @param:JsonProperty("args") val args: List<Any?>? = null,
)