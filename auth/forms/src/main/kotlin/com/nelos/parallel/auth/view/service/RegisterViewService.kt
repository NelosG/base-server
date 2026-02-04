package com.nelos.parallel.auth.view.service

import com.nelos.parallel.auth.service.AuthService
import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.vo.SignData
import com.nelos.parallel.commons.view.service.ViewService
import jakarta.servlet.http.HttpServletResponse

/**
 * View service handling user registration.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.registerViewService", public = true)
class RegisterViewService(
    private val userService: UserDetailsProviderService,
    private val authService: AuthService,
) {

    /**
     * Registers a new user with the provided [data], authenticates them, and returns a success indicator.
     */
    fun signUp(data: SignData, response: HttpServletResponse) {
        userService.signUp(data)
        authService.authenticate(data, response)
    }
}
