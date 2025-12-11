package com.nelos.parallel.auth.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
class UserNotFoundException : RuntimeException {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)
}