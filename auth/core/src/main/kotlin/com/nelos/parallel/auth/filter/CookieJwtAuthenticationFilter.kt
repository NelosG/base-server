package com.nelos.parallel.auth.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Servlet filter that extracts a JWT token from a cookie and authenticates the request.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class CookieJwtAuthenticationFilter(
    private val authenticationManager: AuthenticationManager,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val cookieAuth = request.cookies?.firstOrNull { cookie -> COOKIE_NAME == cookie.name }

        if (cookieAuth != null && SecurityContextHolder.getContext().authentication == null) {
            val authenticationToken = PreAuthenticatedAuthenticationToken(null, cookieAuth.value).apply {
                details = WebAuthenticationDetailsSource().buildDetails(request)
            }

            try {
                val authenticated = authenticationManager.authenticate(authenticationToken)
                SecurityContextHolder.getContext().authentication = authenticated
            } catch (e: AuthenticationException) {
                LOG.debug("Cookie JWT authentication failed for request {}: {}", request.requestURI, e.message)
            }
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(CookieJwtAuthenticationFilter::class.java)

        const val COOKIE_NAME: String = "token"
    }
}
