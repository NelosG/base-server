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
 * Servlet filter that authenticates requests using an API key from the `X-API-Key` header.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class ApiKeyAuthFilter(
    private val apiKeyService: ApiKeyService,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return !path.startsWith("/api/register") && !path.startsWith("/api/callback/")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val apiKey = request.getHeader(HEADER_NAME)

        if (!apiKey.isNullOrBlank()) {
            if (apiKeyService.validateKey(apiKey)) {
                val authentication = ApiKeyAuthenticationToken(apiKey.substring(0, 8))
                SecurityContextHolder.getContext().authentication = authentication
            } else {
                LOG.debug("Invalid API key for request {}", request.requestURI)
            }
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        const val HEADER_NAME = "X-API-Key"
        private val LOG = LoggerFactory.getLogger(ApiKeyAuthFilter::class.java)
    }
}
