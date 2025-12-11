package com.nelos.parallel.auth.controller

import com.nelos.parallel.auth.filter.CookieJwtAuthenticationFilter
import com.nelos.parallel.auth.service.JwtTokenProvider
import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.vo.SignData
import com.nelos.parallel.auth.vo.UserData
import com.nelos.parallel.auth.vo.UserVo
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.net.URI
import javax.naming.AuthenticationException
import kotlin.time.Duration.Companion.days


/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Controller
class LoginController @Autowired constructor(
    private val authenticationManager: AuthenticationManager,
    private val service: UserDetailsProviderService,
    private val tokenService: JwtTokenProvider,

    ) {

    @RequestMapping("/login")
    fun login(): String {
        return "login"
    }

    @ModelAttribute("data")
    fun userRegistrationDto(): SignData {
        return SignData()
    }

    @PostMapping("login/sign-up")
    fun signUp(@ModelAttribute("data") data: SignData): ResponseEntity<*> {
        val createdUser = service.signUp(data)
        return ResponseEntity.created(
            URI.create("/users/" + createdUser.login + "/profile")
        ).body(
            UserVo(
                login = createdUser.login ?: error("Login can't be null"),
            )
        )
    }

    @PostMapping("login/sign-in")
    fun signIn(
        @ModelAttribute("data") data: SignData,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<UserVo> {
        val usernamePassword = UsernamePasswordAuthenticationToken(data.login, data.password)
        val authentication = try {
            authenticationManager.authenticate(usernamePassword)
        } catch (ex: AuthenticationException) {
            throw ex
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

        return ResponseEntity.ok(
            UserVo(
                login = user.login,
            )
        )
    }

    @PostMapping("/logout")
    fun signOut(@AuthenticationPrincipal user: UserData): ResponseEntity<Unit> {
        SecurityContextHolder.clearContext()
        return ResponseEntity.noContent().build()
    }
}