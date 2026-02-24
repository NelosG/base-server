package com.nelos.parallel.auth.exceptions

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Exception thrown when attempting to register a user with a login that already exists.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
class UserAlreadyExistsException(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)