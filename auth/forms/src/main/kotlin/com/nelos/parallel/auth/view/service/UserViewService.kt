package com.nelos.parallel.auth.view.service

import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.auth.view.vo.UserDetailedView
import com.nelos.parallel.auth.vo.ChangePasswordData
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import org.springframework.context.ApplicationContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

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
    private val applicationContext: ApplicationContext,
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
            features = detectFeatures(),
        )
    }

    /**
     * Maps feature-flag names to the ViewService bean names that back them.
     * Front-end nav links carry `data-navbar-feature="..."` and only render
     * when the corresponding flag is in this set - so e.g. the AMQP Config
     * link disappears entirely when the rabbit-forms jar isn't deployed.
     */
    private fun detectFeatures(): Set<String> {
        val map = mapOf(
            FEATURE_HTTP_ADAPTER to "prl.httpAdapterConfigViewService",
            FEATURE_RABBIT_ADAPTER to "prl.rabbitAdapterConfigViewService",
        )
        return map.filter { (_, bean) -> applicationContext.containsBean(bean) }.keys
    }

    fun changePassword(data: ChangePasswordData) {
        val auth = SecurityContextHolder.getContext().authentication
        val login = auth.name
        val newPassword = data.newPassword ?: error("newPassword is required")
        // Server-side length check - the front-end already enforces this, but the
        // ViewEngine endpoint is reachable directly via POST so we must not trust the UI.
        require(newPassword.length >= MIN_PASSWORD_LENGTH) {
            "newPassword must be at least $MIN_PASSWORD_LENGTH characters"
        }
        require(newPassword.length <= MAX_PASSWORD_LENGTH) {
            "newPassword must not exceed $MAX_PASSWORD_LENGTH characters"
        }

        // OTP users (passwordChangeRequired=true) are coming from the forced
        // password-change flow - they've already authenticated with the
        // one-time password to reach this page, so requiring it again is
        // friction without security gain. Established users must still prove
        // possession of the current password.
        val user = userService.findByLogin(login) ?: error("Current user not found")
        val otp = user.properties?.passwordChangeRequired == true
        if (!otp) {
            val currentPassword = data.currentPassword ?: error("currentPassword is required")
            val userData = userDetailsService.loadUserByUsername(login)
            if (!BCryptPasswordEncoder().matches(currentPassword, userData.encryptedPassword)) {
                error("Current password is incorrect")
            }
        }

        userDetailsService.changePassword(login, newPassword)
    }

    @Transactional(propagation = Propagation.REQUIRED)
    fun changeDisplayName(displayName: String) {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        val auth = SecurityContextHolder.getContext().authentication
        // FOR UPDATE so a concurrent password change / reset on the same user
        // doesn't race against our displayName write.
        val user = userService.findByLoginForUpdate(auth.name)
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

        const val FEATURE_HTTP_ADAPTER = "adapter-http-forms"
        const val FEATURE_RABBIT_ADAPTER = "adapter-rabbit-forms"
    }
}
