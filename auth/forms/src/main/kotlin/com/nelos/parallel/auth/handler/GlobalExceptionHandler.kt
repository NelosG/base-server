package com.nelos.parallel.auth.handler

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * Global exception handler that logs unhandled exceptions and forwards to the error page.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ControllerAdvice(name = "prl.globalExceptionHandler")
class GlobalExceptionHandler {

    @ExceptionHandler(Throwable::class)
    fun exception(throwable: Throwable, request: HttpServletRequest): String {
        LOG.error(
            "Exception during form='{}' request={}",
            request.getHeader("Referer"),
            request.requestURI,
            throwable,
        )
        return "forward:/error"
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
