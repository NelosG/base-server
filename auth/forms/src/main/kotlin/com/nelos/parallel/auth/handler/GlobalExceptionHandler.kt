package com.nelos.parallel.auth.handler

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * Global exception handler that logs unhandled exceptions and forwards to the error page.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ControllerAdvice(name = "prl.globalExceptionHandler")
class GlobalExceptionHandler {

    /**
     * Static-resource 404s are routine - browsers (Chrome DevTools especially)
     * probe well-known paths like /.well-known/appspecific/com.chrome.devtools.json,
     * /favicon.ico, /robots.txt regardless of whether the app serves them.
     * Returning a plain 404 with a one-line debug log is enough; the full
     * exception() handler below would dump a 100-frame stack trace per probe.
     */
    @ExceptionHandler(NoResourceFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(ex: NoResourceFoundException, request: HttpServletRequest) {
        if (LOG.isDebugEnabled) LOG.debug("404 {} {}", request.method, request.requestURI)
    }

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
