package com.nelos.parallel.auth.filter

import com.nelos.parallel.auth.service.ApiKeyService
import com.nelos.parallel.auth.token.ApiKeyAuthenticationToken
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Servlet filter that authenticates requests using an API key from the X-API-Key header.
 *
 * The filter applies only to API-key-protected paths (/api/register, /api/callback,
 * /api/pipeline subtrees). For these paths a missing or invalid key short-circuits the
 * chain with a 401 - defence-in-depth on top of the role-based rules in WebSecurityConfig.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class ApiKeyAuthFilter(
    private val apiKeyService: ApiKeyService,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return !path.startsWith("/api/register") && !path.startsWith("/api/callback/") && !path.startsWith("/api/pipeline/")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val apiKey = request.getHeader(HEADER_NAME)

        if (apiKey.isNullOrBlank()) {
            sendUnauthorized(response, "Missing $HEADER_NAME header")
            return
        }
        if (!apiKeyService.validateKey(apiKey)) {
            LOG.debug("Invalid API key for request {}", request.requestURI)
            sendUnauthorized(response, "Invalid API key")
            return
        }

        SecurityContextHolder.getContext().authentication =
            ApiKeyAuthenticationToken(apiKey.substring(0, 8))
        filterChain.doFilter(request, response)
    }

    private fun sendUnauthorized(response: HttpServletResponse, message: String) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.writer.write("""{"error":"$message"}""")
    }

    companion object {
        const val HEADER_NAME = "X-API-Key"
        private val LOG = LoggerFactory.getLogger(ApiKeyAuthFilter::class.java)
    }
}
