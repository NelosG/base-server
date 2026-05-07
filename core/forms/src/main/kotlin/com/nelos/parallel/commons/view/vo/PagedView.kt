package com.nelos.parallel.commons.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Paged response wrapper for ViewService methods that return a slice of a
 * larger list. The Prl UI framework recognises this shape (vs a raw
 * `List<T>`) and renders a "Load more" affordance when [hasMore] is true.
 *
 * `total` is optional - many list queries can compute "is there more" with a
 * single peek-ahead probe but can't afford a full COUNT(*); leaving it null
 * tells the UI to hide the "X of Y" footer.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class PagedView<T> @JsonCreator constructor(
    @param:JsonProperty("items") val items: List<T>,
    @param:JsonProperty("hasMore") val hasMore: Boolean,
    @param:JsonProperty("total") val total: Long? = null,
)
