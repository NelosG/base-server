package com.nelos.parallel.auth.view.service

import com.nelos.parallel.auth.view.vo.UserDetailedView
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import org.springframework.security.core.context.SecurityContextHolder

/**
 * View service providing current user information for the index page.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.userViewService")
class UserViewService {

    /**
     * Returns a map containing the authenticated user's roles (without the Spring Security prefix).
     */
    fun getUserInfo(): UserDetailedView {
        val auth = SecurityContextHolder.getContext().authentication
        val roles = auth.authorities.map { it.authority.removePrefix(AppRole.PREFIX) }
        return UserDetailedView(login = auth.name, roles = roles)
    }
}
