package com.nelos.parallel.auth.filter

import com.auth0.jwt.exceptions.JWTVerificationException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter

/**
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
            val authenticationToken = PreAuthenticatedAuthenticationToken(null, cookieAuth.value)
            authenticationToken.details = WebAuthenticationDetailsSource().buildDetails(request)

            try {
                authenticationManager.authenticate(authenticationToken)
            } catch (e: JWTVerificationException) {
                // do nothing
            }
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        const val COOKIE_NAME: String = "token"
    }
}