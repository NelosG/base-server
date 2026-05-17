package com.nelos.parallel.auth.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class JwtAuthFilterTest {

    private val authenticationManager: AuthenticationManager = mock()
    private val filter = JwtAuthFilter(authenticationManager)

    private lateinit var request: HttpServletRequest
    private lateinit var response: HttpServletResponse
    private lateinit var chain: FilterChain

    @BeforeEach
    fun setUp() {
        request = mock()
        response = mock()
        chain = mock()
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `no Authorization header - chain continues with no authentication`() {
        whenever(request.getHeader("Authorization")).thenReturn(null)

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(eq(request), eq(response))
        verify(authenticationManager, never()).authenticate(any())
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `non-Bearer scheme is ignored`() {
        whenever(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz")

        filter.doFilter(request, response, chain)

        verify(authenticationManager, never()).authenticate(any())
        verify(chain).doFilter(any(), any())
    }

    @Test
    fun `valid Bearer token sets the authentication from the AuthenticationManager`() {
        whenever(request.getHeader("Authorization")).thenReturn("Bearer my-token-value")
        val expectedAuth = TestingAuthenticationToken("alice", null, "ROLE_STUDENT")
        whenever(authenticationManager.authenticate(any())).thenReturn(expectedAuth)

        filter.doFilter(request, response, chain)

        val captor = argumentCaptor<PreAuthenticatedAuthenticationToken>()
        verify(authenticationManager).authenticate(captor.capture())
        // The credentials carry the token string.
        assertEquals("my-token-value", captor.firstValue.credentials)
        assertNotNull(SecurityContextHolder.getContext().authentication)
        assertEquals("alice", SecurityContextHolder.getContext().authentication.principal)
    }

    @Test
    fun `Bearer token rejected by AuthenticationManager leaves the context empty without throwing`() {
        whenever(request.getHeader("Authorization")).thenReturn("Bearer junk")
        whenever(authenticationManager.authenticate(any())).doThrow(BadCredentialsException("nope"))

        filter.doFilter(request, response, chain)

        // No exception escapes; chain still proceeds.
        verify(chain).doFilter(any(), any())
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `Bearer with only whitespace token is ignored`() {
        whenever(request.getHeader("Authorization")).thenReturn("Bearer    ")

        filter.doFilter(request, response, chain)

        verify(authenticationManager, never()).authenticate(any())
    }
}
