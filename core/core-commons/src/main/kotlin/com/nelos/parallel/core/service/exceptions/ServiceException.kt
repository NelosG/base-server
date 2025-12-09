package com.nelos.parallel.core.service.exceptions

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class ServiceException : RuntimeException {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)
}
