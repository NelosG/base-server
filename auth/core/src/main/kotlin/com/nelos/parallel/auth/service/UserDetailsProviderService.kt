package com.nelos.parallel.auth.service

import com.nelos.parallel.auth.exceptions.UserAlreadyExistsException
import com.nelos.parallel.auth.vo.SignData
import com.nelos.parallel.auth.vo.UserData
import com.nelos.parallel.entity.User
import com.nelos.parallel.enums.UserType
import com.nelos.parallel.service.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service
class UserDetailsProviderService : UserDetailsService {
    @Autowired
    lateinit var repository: UserService

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

    fun signUp(data: SignData, isAdmin: Boolean = false): User {
        if (repository.findByLogin(data.login ?: error("Login can't be null")) != null) {
            throw UserAlreadyExistsException("User with login ${data.login} already exists")
        }

        val encryptedPassword = BCryptPasswordEncoder().encode(data.password)

        val type = if (isAdmin) {
            UserType.ADMIN
        } else {
            UserType.USER
        }

        return repository.save(
            User().apply {
                this.login = data.login
                this.encryptedPassword = encryptedPassword
                this.type = type
            }
        )
    }
}