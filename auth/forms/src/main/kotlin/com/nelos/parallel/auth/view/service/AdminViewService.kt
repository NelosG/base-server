package com.nelos.parallel.auth.view.service

import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.view.vo.UserView
import com.nelos.parallel.auth.vo.SignData
import com.nelos.parallel.commons.view.service.ViewService

/**
 * View service for admin-only operations.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.adminViewService")
class AdminViewService(private val userService: UserDetailsProviderService) {

    /**
     * Creates a new admin user with the provided [data] and returns the login.
     */
    fun createAdmin(data: SignData): UserView {
        val user = userService.signUp(data, isAdmin = true)
        return UserView(login = user.login ?: error("User login must not be null"))
    }
}
