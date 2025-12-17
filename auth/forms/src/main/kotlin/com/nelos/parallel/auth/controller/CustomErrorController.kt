package com.nelos.parallel.auth.controller

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION
import jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import java.time.LocalDateTime

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Controller
class CustomErrorController : ErrorController {

    @RequestMapping("/error")
    fun handleError(
        request: HttpServletRequest,
        response: HttpServletResponse,
        model: Model
    ): String {
        // Получаем статус ошибки
        val status = getHttpStatus(request, response).value()
        val exception = request.getAttribute(ERROR_EXCEPTION) as? Exception
        val requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) as? String
        val referer = request.getHeader("Referer")

        // Устанавливаем статус для ответа
        response.status = status

        // Добавляем информацию в модель
        model.addAttribute("status", status)
        model.addAttribute(
            "errorMessage",
            exception?.message ?: when (status) {
                404 -> "Page not found"
                403 -> "Access denied"
                500 -> "Internal server error"
                else -> "An error occurred"
            }
        )
        model.addAttribute("path", requestUri ?: request.requestURI)
        model.addAttribute("referer", referer)
        model.addAttribute("timestamp", LocalDateTime.now())

        return "error"
    }

    private fun getHttpStatus(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): HttpStatusCode {

        (request.getAttribute(ERROR_EXCEPTION) as? Throwable)?.let { ex ->

            ex::class.java.getAnnotation(ResponseStatus::class.java)?.let {
                return it.value
            }

            (ex as? ErrorResponse)?.let {
                return it.statusCode
            }
        }

        (request.getAttribute(ERROR_STATUS_CODE) as? Int)?.let {
            return HttpStatus.valueOf(it)
        }
        (request.getAttribute("preserved_status") as? Int)?.let {
            return HttpStatus.valueOf(it)
        }
        if (response.status != 200) {
            return HttpStatus.valueOf(response.status)
        }

        return HttpStatus.INTERNAL_SERVER_ERROR
    }
}