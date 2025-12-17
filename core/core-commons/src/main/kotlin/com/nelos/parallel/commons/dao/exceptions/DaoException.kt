package com.nelos.parallel.commons.dao.exceptions

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class DaoException : RuntimeException {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)
}