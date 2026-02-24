package com.nelos.parallel.auth.provider

import com.auth0.jwt.exceptions.JWTVerificationException
import com.nelos.parallel.auth.service.JwtTokenProvider
import com.nelos.parallel.auth.service.UserDetailsProviderService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Authentication provider that validates JWT tokens and sets the security context.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.jwtAuthenticationProvider")
class JwtAuthenticationProvider @Autowired constructor(
    private val tokenService: JwtTokenProvider,
    private val userDetailsService: UserDetailsProviderService,
) : AuthenticationProvider {

    /**
     * Authenticates a [PreAuthenticatedAuthenticationToken] by validating the JWT and loading the user.
     */
    override fun authenticate(authentication: Authentication): Authentication {
        check(authentication is PreAuthenticatedAuthenticationToken) { "Unsupported authentication type" }

        val token = (authentication.credentials as? String)
            ?: throw BadCredentialsException("JWT token is missing")
        val login = try {
            tokenService.validateToken(token)
        } catch (e: JWTVerificationException) {
            throw BadCredentialsException("Invalid JWT token: ${e.message}", e)
        }
        val user = userDetailsService.loadUserByUsername(login)

        val authenticationToken = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        SecurityContextHolder.getContext().authentication = authenticationToken
        return authenticationToken
    }

    override fun supports(authentication: Class<*>): Boolean {
        return PreAuthenticatedAuthenticationToken::class.java.isAssignableFrom(authentication)
    }
}