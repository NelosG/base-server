package com.nelos.parallel.auth.service

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.entity.properties.UserProperties
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.exceptions.UserAlreadyExistsException
import com.nelos.parallel.auth.util.RandomPasswordGenerator
import com.nelos.parallel.auth.vo.UserData
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

/**
 * Loads users for Spring Security and owns the password lifecycle (creation with a one-time
 * random password, admin reset, user-driven change).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.userDetailsProviderService")
class UserDetailsProviderService(
    private val users: UserService,
) : UserDetailsService {

    override fun loadUserByUsername(login: String): UserData {
        val user = users.findByLogin(login)
            ?: throw UsernameNotFoundException("User with login $login not found")
        return UserData(
            id = user.id ?: error("Id can't be null"),
            login = user.login ?: error("Login can't be null"),
            encryptedPassword = user.encryptedPassword ?: error("Password can't be null"),
            type = user.type ?: error("Type can't be null"),
        )
    }

    /**
     * Creates a new user with a freshly generated random password. The plain text is stored
     * once in `properties.initialPassword` and `passwordChangeRequired = true`, so the user
     * must change it on first login. Returns the entity and the plain password for the caller
     * to display once.
     *
     * @throws UserAlreadyExistsException if a user with the same login already exists
     */
    fun createUserWithRandomPassword(login: String, displayName: String?, type: UserType): Pair<User, String> {
        if (users.findByLogin(login) != null) {
            throw UserAlreadyExistsException("User with login $login already exists")
        }
        val raw = RandomPasswordGenerator.generate()
        val user = users.save(User().apply {
            this.login = login
            this.displayName = displayName ?: login
            this.encryptedPassword = BCryptPasswordEncoder().encode(raw)
            this.type = type
            this.properties = UserProperties(
                initialPassword = raw,
                passwordChangeRequired = true,
            )
        })
        return user to raw
    }

    /**
     * Generates and applies a fresh random password for the given user. Used by admins to
     * unblock a user who lost their password - re-arms the OTP flow.
     */
    fun resetPassword(userId: Long): String {
        val user = users.tryFindById(userId)
            ?: throw UsernameNotFoundException("User with id $userId not found")
        val raw = RandomPasswordGenerator.generate()
        user.encryptedPassword = BCryptPasswordEncoder().encode(raw)
        user.properties = (user.properties ?: UserProperties()).also {
            it.initialPassword = raw
            it.passwordChangeRequired = true
        }
        users.save(user)
        return raw
    }

    /**
     * Changes the password of the user identified by [login], clears the one-time-password
     * fields in `properties`, and lifts the password-change requirement.
     */
    fun changePassword(login: String, newPassword: String) {
        val user = users.findByLogin(login)
            ?: throw UsernameNotFoundException("User with login $login not found")
        user.encryptedPassword = BCryptPasswordEncoder().encode(newPassword)
        user.properties?.let {
            it.initialPassword = null
            it.passwordChangeRequired = false
        }
        users.save(user)
    }
}
