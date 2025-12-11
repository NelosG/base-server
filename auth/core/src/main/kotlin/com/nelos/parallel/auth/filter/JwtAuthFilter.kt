package com.nelos.parallel.auth.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter

/**
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
        val requestTokenHeader = request.getHeader("Authorization") //Extract the Authorization Header:


        if (requestTokenHeader == null || !requestTokenHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }


        //Ensure that the token in the header is correctly formatted as "Bearer <your-jwt-token>".
        val token = requestTokenHeader.split("Bearer ".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()[1] // Extract JWT token from the header

        val authenticationToken = PreAuthenticatedAuthenticationToken(null, token)
        authenticationToken.details = WebAuthenticationDetailsSource().buildDetails(request)

        authenticationManager.authenticate(authenticationToken)

        //Continue the Filter Chain
        filterChain.doFilter(request, response)
    }
}