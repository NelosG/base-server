package com.nelos.parallel.auth.view.service

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.auth.view.vo.CreatedUserView
import com.nelos.parallel.auth.view.vo.UserView
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService

/**
 * Admin-only user management: list non-student users, create admins, reset
 * passwords, delete admins (except the bootstrap "admin" account).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.userMgmtViewService", roles = [AppRole.ADMIN])
class UserMgmtViewService(
    private val userService: UserService,
    private val userDetailsService: UserDetailsProviderService,
) {

    /** All users except students - used for the admin management page. */
    fun getAdmins(): List<UserView> =
        userService.findAll()
            .filter { it.type != UserType.STUDENT }
            .map { it.toView() }

    fun createAdmin(login: String, displayName: String?): CreatedUserView {
        val (user, raw) = userDetailsService.createUserWithRandomPassword(login, displayName, UserType.ADMIN)
        return CreatedUserView(
            id = user.id ?: error("User has no id after save"),
            login = user.login ?: error("User has no login"),
            password = raw,
        )
    }

    /** Resets any user's password to a fresh random one. Returns the plain text once. */
    fun resetPassword(userId: Long): String = userDetailsService.resetPassword(userId)

    fun deleteAdmin(login: String) {
        if (login == DEFAULT_ADMIN_LOGIN) error("Cannot delete the default admin user")
        val user = userService.findByLogin(login) ?: error("User '$login' not found")
        if (user.type == UserType.STUDENT) error("Use the students page to delete a student")
        userService.remove(user)
    }

    private fun User.toView() = UserView(
        id = id,
        login = login,
        displayName = displayName,
        type = type?.name,
    )

    companion object {
        private const val DEFAULT_ADMIN_LOGIN = "admin"
    }
}
