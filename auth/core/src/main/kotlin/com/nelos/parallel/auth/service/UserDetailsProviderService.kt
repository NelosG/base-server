package com.nelos.parallel.auth.service

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.exceptions.UserAlreadyExistsException
import com.nelos.parallel.auth.vo.SignData
import com.nelos.parallel.auth.vo.UserData
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

/**
 * User details service that loads users from the database and handles sign-up.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.userDetailsProviderService")
class UserDetailsProviderService(
    private val repository: UserService,
) : UserDetailsService {

    /**
     * Loads the user by [login] and returns a [UserData] instance for Spring Security.
     *
     * @throws UsernameNotFoundException if no user with the given login exists
     */
    override fun loadUserByUsername(login: String): UserData {
        val user = repository.findByLogin(login)
            ?: throw UsernameNotFoundException("User with login $login not found")
        return UserData(
            id = user.id ?: error("Id can't be null"),
            login = user.login ?: error("Login can't be null"),
            encryptedPassword = user.encryptedPassword ?: error("Password can't be null"),
            type = user.type ?: error("Type can't be null"),
        )
    }

    /**
     * Registers a new user with the given [data].
     *
     * @param isAdmin if `true`, the user is created with admin privileges
     * @return the persisted [User] entity
     * @throws UserAlreadyExistsException if a user with the same login already exists
     */
    fun signUp(data: SignData, isAdmin: Boolean = false): User {
        val login = data.login ?: error("Login can't be null")
        val password = data.password ?: error("Password can't be null")

        if (repository.findByLogin(login) != null) {
            throw UserAlreadyExistsException("User with login $login already exists")
        }

        val type = if (isAdmin) UserType.ADMIN else UserType.USER

        return try {
            repository.save(
                User().apply {
                    this.login = login
                    this.encryptedPassword = BCryptPasswordEncoder().encode(password)
                    this.type = type
                }
            )
        } catch (_: DataIntegrityViolationException) {
            throw UserAlreadyExistsException("User with login $login already exists")
        }
    }
}
