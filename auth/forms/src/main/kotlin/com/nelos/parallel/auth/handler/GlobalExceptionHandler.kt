package com.nelos.parallel.auth.handler

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(Throwable::class)
    fun exception(
        throwable: Exception?,
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): String {
        val referer = request.getHeader("Referer")
        val requestUri = request.requestURI
        logger.error(
            "Exception during form='${referer}' request=${requestUri}",
            throwable
        )

        return "forward:/error"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}