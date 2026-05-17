package com.nelos.parallel.auth.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
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
class CookieJwtAuthenticationFilterTest {

    private val authenticationManager: AuthenticationManager = mock()
    private val filter = CookieJwtAuthenticationFilter(authenticationManager)

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
    fun `no cookies - chain continues with no authentication attempted`() {
        whenever(request.cookies).thenReturn(null)

        filter.doFilter(request, response, chain)

        verify(authenticationManager, never()).authenticate(any())
        verify(chain).doFilter(eq(request), eq(response))
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `cookie present but a different one - auth is not attempted`() {
        whenever(request.cookies).thenReturn(arrayOf(Cookie("session", "x")))

        filter.doFilter(request, response, chain)

        verify(authenticationManager, never()).authenticate(any())
    }

    @Test
    fun `token cookie attempts authentication and stores the result`() {
        whenever(request.cookies).thenReturn(arrayOf(Cookie("token", "jwt-from-cookie")))
        val expected = TestingAuthenticationToken("bob", null, "ROLE_ADMIN")
        whenever(authenticationManager.authenticate(any())).thenReturn(expected)

        filter.doFilter(request, response, chain)

        val captor = argumentCaptor<PreAuthenticatedAuthenticationToken>()
        verify(authenticationManager).authenticate(captor.capture())
        assertEquals("jwt-from-cookie", captor.firstValue.credentials)
        assertNotNull(SecurityContextHolder.getContext().authentication)
        assertEquals("bob", SecurityContextHolder.getContext().authentication.principal)
    }

    @Test
    fun `invalid cookie value does not propagate the AuthenticationException`() {
        whenever(request.cookies).thenReturn(arrayOf(Cookie("token", "rotten")))
        whenever(authenticationManager.authenticate(any())).doThrow(BadCredentialsException("bad cookie"))

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(any(), any())
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `already-authenticated context is not overwritten by the cookie`() {
        whenever(request.cookies).thenReturn(arrayOf(Cookie("token", "any")))
        val preExisting = TestingAuthenticationToken("preset-user", null, "ROLE_USER")
        SecurityContextHolder.getContext().authentication = preExisting

        filter.doFilter(request, response, chain)

        verify(authenticationManager, never()).authenticate(any())
        assertEquals(preExisting, SecurityContextHolder.getContext().authentication)
    }
}
