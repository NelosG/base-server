package com.nelos.parallel.commons.view.vo

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Standardized response returned by the ViewEngine, containing a success flag and optional data or error.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class ViewResponse(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null,
) {
    companion object {
        fun ok(data: Any? = null): ViewResponse = ViewResponse(success = true, data = data)
        fun error(message: String): ViewResponse = ViewResponse(success = false, error = message)
    }
}