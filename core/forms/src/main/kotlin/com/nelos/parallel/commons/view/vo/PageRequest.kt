package com.nelos.parallel.commons.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Slice descriptor passed from the Prl UI framework to ViewService methods
 * that return [PagedView]. The framework always supplies `offset >= 0` and
 * `limit > 0`; service code should still clamp `limit` to a sane maximum.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class PageRequest @JsonCreator constructor(
    @param:JsonProperty("offset") val offset: Int = 0,
    @param:JsonProperty("limit") val limit: Int = 50,
)
