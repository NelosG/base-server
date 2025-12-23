package com.nelos.parallel.commons.service.exceptions

/**
 * Exception thrown when a service-layer operation fails.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class ServiceException : RuntimeException {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)
}
