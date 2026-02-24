package com.nelos.parallel.commons.adapter.exceptions

/**
 * Base exception for adapter-related errors.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
open class AdapterException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)