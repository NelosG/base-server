package com.nelos.parallel.auth.controller

import com.nelos.parallel.auth.service.JwtTokenProvider
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
class LoginController @Autowired constructor(
    authenticationManager: AuthenticationManager,
    tokenService: JwtTokenProvider,
) : AbstractAuthController(
    authenticationManager,
    tokenService,
) {

    @RequestMapping("/login")
    fun login(
        @RequestParam(value = "unauthorized", required = false) unauthorized: String?,
        @RequestParam(value = "error", required = false) error: String?,
        @RequestParam(value = "logout", required = false) logout: String?,
        model: Model
    ): String {
        if (unauthorized != null) {
            model.addAttribute("errorMessage", "Unauthorized!")
        }
        if (error != null) {
            model.addAttribute("errorMessage", "Invalid username or password!")
        }

        if (logout != null) {
            model.addAttribute("logoutMessage", "Successful logout")
        }

        return "login"
    }

    @ModelAttribute("data")
    fun userRegistrationDto(): SignData {
        return SignData()
    }

    @PostMapping("login/sign-in")
    fun signIn(
        @ModelAttribute("data") data: SignData,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {


//        return ResponseEntity.ok(
//            UserVo(
//                login = user.login,
//            )
//        )
        doSingIn(data, response)
        response.sendRedirect("/")
    }


}
