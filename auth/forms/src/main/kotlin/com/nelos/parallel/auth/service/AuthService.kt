package com.nelos.parallel.auth.service

import com.nelos.parallel.auth.filter.CookieJwtAuthenticationFilter
import com.nelos.parallel.auth.vo.SignData
import com.nelos.parallel.auth.vo.UserData
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.days

/**
 * Service that authenticates users and issues JWT tokens as HTTP cookies.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.authService")
class AuthService(
    private val authenticationManager: AuthenticationManager,
    private val tokenService: JwtTokenProvider,
) {

    /**
     * Authenticates the user with the provided [data] and sets a JWT cookie on the [response].
     */
    fun authenticate(data: SignData, response: HttpServletResponse) {
        val auth = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(data.login, data.password)
        )
        val user = (auth.principal as? UserData)
            ?: throw IllegalStateException("Unexpected principal type: ${auth.principal::class}")
        val accessToken = tokenService.generateAccessToken(user)

        response.addCookie(Cookie(CookieJwtAuthenticationFilter.COOKIE_NAME, accessToken).apply {
            isHttpOnly = true
            secure = true
            maxAge = JwtTokenProvider.EXPIRATION_IN_DAYS.days.inWholeSeconds.toInt()
            path = "/"
        })
    }
}
