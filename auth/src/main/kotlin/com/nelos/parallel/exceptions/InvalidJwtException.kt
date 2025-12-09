package com.nelos.parallel.exceptions

import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
class InvalidJwtException : AuthenticationException {

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)
}