package com.nelos.parallel.auth.provider

import com.auth0.jwt.exceptions.JWTVerificationException
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.service.JwtTokenProvider
import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.vo.UserData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class JwtAuthenticationProviderTest {

    private val tokenService: JwtTokenProvider = mock()
    private val userDetailsService: UserDetailsProviderService = mock()
    private val provider = JwtAuthenticationProvider(tokenService, userDetailsService)

    private fun preAuth(credentials: Any?) = PreAuthenticatedAuthenticationToken("principal", credentials)

    @Test
    fun `supports only PreAuthenticatedAuthenticationToken`() {
        assertTrue(provider.supports(PreAuthenticatedAuthenticationToken::class.java))
        assertFalse(provider.supports(UsernamePasswordAuthenticationToken::class.java))
    }

    @Test
    fun `valid token resolves to an authenticated UsernamePasswordAuthenticationToken with user authorities`() {
        whenever(tokenService.validateToken("good-token")).thenReturn("alice")
        whenever(userDetailsService.loadUserByUsername("alice"))
            .thenReturn(UserData(1L, "alice", "hash", UserType.STUDENT))

        val result = provider.authenticate(preAuth("good-token"))

        assertTrue(result is UsernamePasswordAuthenticationToken)
        val ud = result.principal as UserData
        assertEquals("alice", ud.username)
        assertEquals(UserType.STUDENT, ud.type)
        assertTrue(result.authorities.isNotEmpty(), "authorities must mirror the user's role list")
    }

    @Test
    fun `non-string credentials throws BadCredentialsException`() {
        // String? cast yields null when credentials is not a String - provider treats it
        // as "token missing".
        val ex = assertThrows<BadCredentialsException> {
            provider.authenticate(preAuth(credentials = 12345))
        }
        assertTrue(ex.message?.contains("missing") == true)
    }

    @Test
    fun `null credentials throws BadCredentialsException`() {
        assertThrows<BadCredentialsException> { provider.authenticate(preAuth(credentials = null)) }
    }

    @Test
    fun `JWTVerificationException is wrapped as BadCredentialsException`() {
        whenever(tokenService.validateToken(any())).doThrow(object : JWTVerificationException("expired") {})

        val ex = assertThrows<BadCredentialsException> { provider.authenticate(preAuth("bad-token")) }
        assertTrue(ex.message?.contains("expired") == true)
        // The original exception is preserved as the cause for diagnostics.
        assertTrue(ex.cause is JWTVerificationException)
    }

    @Test
    fun `unsupported authentication type is rejected up-front`() {
        // The provider explicitly requires PreAuthenticatedAuthenticationToken.
        assertThrows<IllegalStateException> {
            provider.authenticate(UsernamePasswordAuthenticationToken("u", "p"))
        }
    }
}
