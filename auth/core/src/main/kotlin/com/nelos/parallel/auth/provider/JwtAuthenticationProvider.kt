package com.nelos.parallel.auth.provider

import com.nelos.parallel.auth.service.JwtTokenProvider
import com.nelos.parallel.auth.service.UserDetailsProviderService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component
class JwtAuthenticationProvider @Autowired constructor(
    private val tokenService: JwtTokenProvider,
    private val userDetailsService: UserDetailsProviderService,
) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication {
        when (authentication) {
            is PreAuthenticatedAuthenticationToken -> {
                val token = authentication.credentials as String
                val login = tokenService.validateToken(token)
                val user = userDetailsService.loadUserByUsername(login)

                //Create an Authentication Token
                val authenticationToken = UsernamePasswordAuthenticationToken(
                    user, null, user.authorities
                )

                //Set Authentication in Security Context
                SecurityContextHolder.getContext().authentication = authenticationToken
                return authenticationToken
            }

            else -> error("Unsupported authentication type")
        }
    }

    override fun supports(authentication: Class<*>): Boolean {
        return authentication.isAssignableFrom(PreAuthenticatedAuthenticationToken::class.java)
    }
}