package com.nelos.parallel.auth.view.service

import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.auth.view.vo.UserDetailedView
import com.nelos.parallel.auth.vo.ChangePasswordData
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * View service exposing the current user's profile (login, display name, roles, OTP flag)
 * and the operations they may perform on themselves: change password, change display name.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.userViewService")
class UserViewService(
    private val userDetailsService: UserDetailsProviderService,
    private val userService: UserService,
) {

    fun getUserInfo(): UserDetailedView {
        val auth = SecurityContextHolder.getContext().authentication
        val roles = auth.authorities.map { it.authority.removePrefix(AppRole.PREFIX) }
        val user = userService.findByLogin(auth.name)
        return UserDetailedView(
            login = auth.name,
            displayName = user?.displayName,
            roles = roles,
            passwordChangeRequired = user?.properties?.passwordChangeRequired == true,
        )
    }

    fun changePassword(data: ChangePasswordData) {
        val auth = SecurityContextHolder.getContext().authentication
        val login = auth.name
        val currentPassword = data.currentPassword ?: error("currentPassword is required")
        val newPassword = data.newPassword ?: error("newPassword is required")
        // Server-side length check - the front-end already enforces this, but the
        // ViewEngine endpoint is reachable directly via POST so we must not trust the UI.
        require(newPassword.length >= MIN_PASSWORD_LENGTH) {
            "newPassword must be at least $MIN_PASSWORD_LENGTH characters"
        }
        require(newPassword.length <= MAX_PASSWORD_LENGTH) {
            "newPassword must not exceed $MAX_PASSWORD_LENGTH characters"
        }

        val userData = userDetailsService.loadUserByUsername(login)
        if (!BCryptPasswordEncoder().matches(currentPassword, userData.encryptedPassword)) {
            error("Current password is incorrect")
        }

        userDetailsService.changePassword(login, newPassword)
    }

    fun changeDisplayName(displayName: String) {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        val auth = SecurityContextHolder.getContext().authentication
        val user = userService.findByLogin(auth.name)
            ?: error("Current user not found")
        user.displayName = displayName
        userService.save(user)
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        // BCrypt silently truncates inputs longer than 72 bytes. Cap at 64 bytes worth of
        // characters so multi-byte Unicode still fits - beyond this BCrypt sees only a prefix
        // and the rest of the password contributes nothing to the hash.
        private const val MAX_PASSWORD_LENGTH = 64
    }
}
