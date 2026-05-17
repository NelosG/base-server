package com.nelos.parallel.auth.filter

import com.nelos.parallel.auth.service.ApiKeyService
import com.nelos.parallel.auth.token.ApiKeyAuthenticationToken
import com.nelos.parallel.commons.security.AppRole
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.security.core.context.SecurityContextHolder
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class ApiKeyAuthFilterTest {

    private val apiKeyService: ApiKeyService = mock()
    private val filter = ApiKeyAuthFilter(apiKeyService)

    private lateinit var request: HttpServletRequest
    private lateinit var response: HttpServletResponse
    private lateinit var chain: FilterChain
    private lateinit var responseWriter: StringWriter

    @BeforeEach
    fun setUp() {
        request = mock()
        response = mock()
        chain = mock()
        responseWriter = StringWriter()
        whenever(response.writer).thenReturn(PrintWriter(responseWriter))
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun setPath(path: String) {
        whenever(request.servletPath).thenReturn(path)
    }

    // --- exempt-path matrix (behavioural - observed via doFilter) -------

    @Test
    fun `request to non-protected path bypasses the filter entirely`() {
        setPath("/login")
        // No X-API-Key header set, but the filter must not 401 us.
        filter.doFilter(request, response, chain)

        verify(response, never()).status = HttpServletResponse.SC_UNAUTHORIZED
        verify(apiKeyService, never()).validateKey(any())
        verify(chain).doFilter(any(), any())
    }

    @Test
    fun `protected callback path requires a valid api key`() {
        setPath("/api/callback/result")
        whenever(request.getHeader("X-API-Key")).thenReturn(null)

        filter.doFilter(request, response, chain)

        verify(response).status = HttpServletResponse.SC_UNAUTHORIZED
    }

    // --- doFilterInternal -----------------------------------------------

    @Test
    fun `missing header on protected path returns 401 and short-circuits the chain`() {
        setPath("/api/register")
        whenever(request.getHeader("X-API-Key")).thenReturn(null)

        filter.doFilter(request, response, chain)

        verify(response).status = HttpServletResponse.SC_UNAUTHORIZED
        assertTrue(responseWriter.toString().contains("Missing"))
        verify(chain, never()).doFilter(any(), any())
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `blank header is treated as missing`() {
        setPath("/api/pipeline/submit")
        whenever(request.getHeader("X-API-Key")).thenReturn("   ")

        filter.doFilter(request, response, chain)

        verify(response).status = HttpServletResponse.SC_UNAUTHORIZED
        verify(chain, never()).doFilter(any(), any())
    }

    @Test
    fun `invalid key returns 401 with the appropriate message`() {
        setPath("/api/pipeline/submit")
        whenever(request.getHeader("X-API-Key")).thenReturn("nope")
        whenever(apiKeyService.validateKey("nope")).thenReturn(false)

        filter.doFilter(request, response, chain)

        verify(response).status = HttpServletResponse.SC_UNAUTHORIZED
        assertTrue(responseWriter.toString().contains("Invalid API key"))
        verify(chain, never()).doFilter(any(), any())
    }

    @Test
    fun `valid key sets ROLE_API_CLIENT authentication and continues the chain`() {
        setPath("/api/pipeline/submit")
        val raw = "abcdef0123456789more"
        whenever(request.getHeader("X-API-Key")).thenReturn(raw)
        whenever(apiKeyService.validateKey(raw)).thenReturn(true)

        filter.doFilter(request, response, chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        assertTrue(auth is ApiKeyAuthenticationToken)
        assertEquals("abcdef01", auth.principal) // first 8 chars of the raw key
        assertTrue(auth.authorities.any { it.authority == AppRole.ROLE_API_CLIENT })
        verify(chain).doFilter(eq(request), eq(response))
    }
}
