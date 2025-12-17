package com.nelos.parallel.auth.controller

import com.nelos.parallel.auth.filter.CookieJwtAuthenticationFilter
import com.nelos.parallel.auth.service.JwtTokenProvider
import com.nelos.parallel.auth.vo.SignData
import com.nelos.parallel.auth.vo.UserData
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import kotlin.time.Duration.Companion.days

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
abstract class AbstractAuthController(
    private val authenticationManager: AuthenticationManager,
    private val tokenService: JwtTokenProvider,
) {

    protected fun doSingIn(data: SignData, response: HttpServletResponse) {
        val usernamePassword = UsernamePasswordAuthenticationToken(data.login, data.password)
        val authentication = try {
            authenticationManager.authenticate(usernamePassword)
        } catch (ex: AuthenticationException) {
            response.sendRedirect("/login?error=true")
            return
        }
        val user = authentication.principal as UserData

        val accessToken = tokenService.generateAccessToken(user)

        val cookie = Cookie(CookieJwtAuthenticationFilter.COOKIE_NAME, accessToken)
        // it makes sure that this cookie cannot be accessed by any other it can only be fund with the help of your Http methods
        // no other attacker can be access our website
        // Prevents JavaScript access to the cookie
        cookie.isHttpOnly = true
        cookie.secure = true
        cookie.maxAge = JwtTokenProvider.EXPIRATION_IN_DAYS.days.inWholeSeconds.toInt()
        cookie.path = "/"
        response.addCookie(cookie)
    }
}