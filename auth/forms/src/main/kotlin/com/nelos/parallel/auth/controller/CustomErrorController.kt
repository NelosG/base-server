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
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Custom error controller that redirects to a user-friendly error page with status and message parameters.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Controller("prl.customErrorController")
class CustomErrorController : ErrorController {

    @GetMapping("/error-page")
    fun errorPage(): String = "error-page"

    /**
     * Handles error requests by extracting the HTTP status, message, and originating path,
     * then redirects to the error page with URL-encoded query parameters.
     */
    @RequestMapping("/error")
    fun handleError(request: HttpServletRequest, response: HttpServletResponse): String {
        val status = getHttpStatus(request, response).value()
        val message = getErrorMessage(status, request).urlEncode()
        val path = ((request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) as? String) ?: "").urlEncode()
        val referer = (request.getHeader("Referer") ?: "/").urlEncode()
        return "redirect:/error-page?status=$status&message=$message&path=$path&referer=$referer"
    }

    private fun getErrorMessage(status: Int, request: HttpServletRequest): String {
        val exception = request.getAttribute(ERROR_EXCEPTION) as? Exception
        return exception?.message ?: when (status) {
            404 -> "Page not found"
            403 -> "Access denied"
            500 -> "Internal server error"
            else -> "An error occurred"
        }
    }

    private fun getHttpStatus(request: HttpServletRequest, response: HttpServletResponse): HttpStatusCode {
        val exception = request.getAttribute(ERROR_EXCEPTION) as? Throwable
        if (exception != null) {
            val annotationStatus = exception::class.java.getAnnotation(ResponseStatus::class.java)
            if (annotationStatus != null) return annotationStatus.value

            if (exception is ErrorResponse) return exception.statusCode
        }

        val errorStatusCode = request.getAttribute(ERROR_STATUS_CODE) as? Int
        if (errorStatusCode != null) return HttpStatus.valueOf(errorStatusCode)

        val preservedStatus = request.getAttribute("preserved_status") as? Int
        if (preservedStatus != null) return HttpStatus.valueOf(preservedStatus)

        if (response.status != 200) return HttpStatus.valueOf(response.status)

        return HttpStatus.INTERNAL_SERVER_ERROR
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)
}
