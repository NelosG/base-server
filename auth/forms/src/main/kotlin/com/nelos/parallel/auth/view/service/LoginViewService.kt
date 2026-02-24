package com.nelos.parallel.auth.view.service

import com.nelos.parallel.auth.service.AuthService
import com.nelos.parallel.auth.vo.SignData
import com.nelos.parallel.commons.view.service.ViewService
import jakarta.servlet.http.HttpServletResponse

/**
 * View service handling user sign-in.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.loginViewService", public = true)
class LoginViewService(private val authService: AuthService) {

    /**
     * Authenticates the user with the provided [data] and returns a success indicator.
     */
    fun signIn(data: SignData, response: HttpServletResponse) {
        authService.authenticate(data, response)
    }
}
