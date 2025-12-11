package com.nelos.parallel.auth.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus


/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ControllerAdvice
class ErrorController {

    @ExceptionHandler(Throwable::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun exception(throwable: Throwable?, model: Model): String {
        logger.error("Exception during execution of SpringSecurity application", throwable)
        val errorMessage = (if (throwable != null) throwable.message else "Unknown error")
        model.addAttribute("errorMessage", errorMessage)
        return "error"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ErrorController::class.java)
    }
}