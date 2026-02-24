package com.nelos.parallel.auth.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Servlet filter that extracts a JWT token from the Authorization header and authenticates the request.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class JwtAuthFilter(
    private val authenticationManager: AuthenticationManager,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization")

        val token = header
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")
            ?.trim()
        if (!token.isNullOrBlank()) {
            val authenticationToken = PreAuthenticatedAuthenticationToken(null, token).apply {
                details = WebAuthenticationDetailsSource().buildDetails(request)
            }
            try {
                authenticationManager.authenticate(authenticationToken)
            } catch (e: AuthenticationException) {
                LOG.debug("JWT authentication failed for request {}: {}", request.requestURI, e.message)
            }
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(JwtAuthFilter::class.java)
    }
}
