package com.nelos.parallel.auth.service

import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.filter.CookieJwtAuthenticationFilter
import com.nelos.parallel.auth.vo.LoginData
import com.nelos.parallel.auth.vo.UserData
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cookie-attribute contract for [AuthService]: any drift here is a security
 * regression (e.g. cookie missing HttpOnly would expose tokens to XSS, missing
 * SameSite on HTTPS would weaken CSRF posture).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class AuthServiceTest {

    private val authenticationManager: AuthenticationManager = mock()
    private val tokenService: JwtTokenProvider = mock()
    private val service = AuthService(authenticationManager, tokenService)

    private val response: HttpServletResponse = mock()
    private lateinit var request: HttpServletRequest

    @BeforeEach
    fun resetRequest() {
        // Plaintext by default - HTTPS branch overrides via `securedRequest()`.
        request = mock { on { isSecure } doReturn false }
    }

    private fun securedRequest(): HttpServletRequest = mock { on { isSecure } doReturn true }

    private fun stubAuth(login: String = LOGIN) {
        val user = UserData(USER_ID, login, ENCRYPTED, UserType.STUDENT)
        whenever(authenticationManager.authenticate(any()))
            .thenReturn(UsernamePasswordAuthenticationToken(user, null, user.authorities))
        whenever(tokenService.generateAccessToken(user)).thenReturn(JWT_VALUE)
    }

    private fun loginData() = LoginData(LOGIN, RAW_PASSWORD)

    private fun captureSetCookie(): String {
        val name = argumentCaptor<String>()
        val value = argumentCaptor<String>()
        verify(response).addHeader(name.capture(), value.capture())
        assertEquals("Set-Cookie", name.firstValue)
        return value.firstValue
    }

    @Test
    fun `delegates the credentials to the AuthenticationManager`() {
        stubAuth()

        service.authenticate(loginData(), request, response)

        val auth = argumentCaptor<UsernamePasswordAuthenticationToken>()
        verify(authenticationManager).authenticate(auth.capture())
        assertEquals(LOGIN, auth.firstValue.name)
        assertEquals(RAW_PASSWORD, auth.firstValue.credentials)
    }

    @Test
    fun `plaintext request sets Lax SameSite and non-secure cookie`() {
        stubAuth()

        service.authenticate(loginData(), request, response)

        val cookie = captureSetCookie()
        assertContains(cookie, "${CookieJwtAuthenticationFilter.COOKIE_NAME}=$JWT_VALUE")
        assertContains(cookie, "HttpOnly")
        assertContains(cookie, "Path=/")
        assertContains(cookie, "SameSite=Lax")
        assertTrue(!cookie.contains("Secure"), "non-HTTPS request must NOT set the Secure flag")
    }

    @Test
    fun `HTTPS request sets Strict SameSite and the Secure flag`() {
        stubAuth()

        service.authenticate(loginData(), securedRequest(), response)

        val cookie = captureSetCookie()
        assertContains(cookie, "Secure")
        assertContains(cookie, "SameSite=Strict")
    }

    @Test
    fun `cookie max-age is configured for the JWT expiration window`() {
        stubAuth()

        service.authenticate(loginData(), request, response)

        val cookie = captureSetCookie()
        val expectedSeconds = JwtTokenProvider.EXPIRATION_IN_DAYS.toLong() * SECONDS_PER_DAY
        assertContains(cookie, "Max-Age=$expectedSeconds")
    }

    @Test
    fun `unexpected principal type after authentication is rejected`() {
        // Pre-auth managers in some setups return raw String principals - we MUST NOT
        // proceed to sign a token over a non-UserData principal.
        whenever(authenticationManager.authenticate(any()))
            .thenReturn(UsernamePasswordAuthenticationToken("string-principal", null, emptyList()))

        assertThrows<IllegalStateException> {
            service.authenticate(loginData(), request, response)
        }
        verify(response, never()).addHeader(any(), any())
    }

    companion object {
        private const val USER_ID = 1L
        private const val LOGIN = "alice"
        private const val RAW_PASSWORD = "pw"
        private const val ENCRYPTED = "hash"
        private const val JWT_VALUE = "test-jwt-token"
        private const val SECONDS_PER_DAY = 24L * 3600L
    }
}
