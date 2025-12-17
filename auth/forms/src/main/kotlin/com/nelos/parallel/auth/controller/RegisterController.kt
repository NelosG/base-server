package com.nelos.parallel.auth.controller

import com.nelos.parallel.auth.exceptions.UserAlreadyExistsException
import com.nelos.parallel.auth.service.JwtTokenProvider
import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.vo.SignData
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam


/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Controller
class RegisterController @Autowired constructor(
    authenticationManager: AuthenticationManager,
    tokenService: JwtTokenProvider,
    private val service: UserDetailsProviderService,
) : AbstractAuthController(
    authenticationManager,
    tokenService,
) {

    @RequestMapping("/register")
    fun login(
        @RequestParam(value = "alreadyExists", required = false) alreadyExists: String?,
        model: Model
    ): String {
        if (alreadyExists != null) {
            model.addAttribute("errorMessage", "User already exists!")
        }
        return "register"
    }

    @ModelAttribute("data")
    fun userRegistrationDto(): SignData {
        return SignData()
    }

    @PostMapping("register/sign-up")
    fun signUp(
        @ModelAttribute("data") data: SignData,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        try {
            service.signUp(data)
        } catch (e: UserAlreadyExistsException) {
            response.sendRedirect("/register?alreadyExists=true")
            return
        }
//        return ResponseEntity.created(
//            URI.create("/users/" + createdUser.login + "/profile")
//        ).body(
//            UserVo(
//                login = createdUser.login ?: error("Login can't be null"),
//            )
//        )
        doSingIn(data, response)
        response.sendRedirect("/")
    }
}
