package com.nelos.parallel.controllers

import com.nelos.parallel.auth.JWTTokenProvider
import com.nelos.parallel.entity.User
import com.nelos.parallel.service.AuthService
import com.nelos.parallel.vo.JwtDto
import com.nelos.parallel.vo.SignInDto
import com.nelos.parallel.vo.SignUpDto
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@RestController
@RequestMapping("/auth")
class AuthController @Autowired constructor(
    private val authenticationManager: AuthenticationManager,
    private val service: AuthService,
    private val tokenService: JWTTokenProvider,
) {

    @PostMapping("/signup")
    fun signUp(@RequestBody @Valid data: SignUpDto): ResponseEntity<*> {
        service.signUp(data)
        return ResponseEntity.status(HttpStatus.CREATED).build<Any>()
    }

    @PostMapping("/signin")
    fun signIn(
        @RequestBody @Valid data: SignInDto,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<JwtDto> {
        val usernamePassword = UsernamePasswordAuthenticationToken(data.login, data.password)

        val authentication = authenticationManager.authenticate(usernamePassword)

        val accessToken = tokenService.generateAccessToken(authentication.principal as User)

        val cookie = Cookie("token", accessToken)
        // it makes sure that this cookie cannot be accessed by any other it can only be fund with the help of your Http methods
        // no other attacker can be access our website
        // Prevents JavaScript access to the cookie
        cookie.isHttpOnly = true;
        response.addCookie(cookie);

        return ResponseEntity.ok(JwtDto(accessToken))
    }
}