package com.nelos.parallel.auth.token

import com.nelos.parallel.commons.security.AppRole
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * Authentication token representing a validated API key.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class ApiKeyAuthenticationToken(
    private val keyName: String,
) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority(AppRole.ROLE_API_CLIENT))) {

    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): String = keyName

    override fun getCredentials(): Any? = null
}
