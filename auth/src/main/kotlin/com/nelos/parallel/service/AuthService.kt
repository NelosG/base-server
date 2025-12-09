package com.nelos.parallel.service

import com.nelos.parallel.entity.User
import com.nelos.parallel.exceptions.InvalidJwtException
import com.nelos.parallel.exceptions.UserAlreadyExistsException
import com.nelos.parallel.vo.SignUpDto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service
class AuthService : UserDetailsService {
    @Autowired
    lateinit var repository: UserService

    override fun loadUserByUsername(username: String): UserDetails? {
        val user = repository.findByLogin(username)
            ?: throw UserAlreadyExistsException("USER WITH EMAIL $username NOT FOUND")
        return user
    }

    fun signUp(data: SignUpDto): UserDetails {
        if (repository.findByLogin(data.login) != null) {
            throw InvalidJwtException("Username already exists")
        }

        val encryptedPassword = BCryptPasswordEncoder().encode(data.password)

        val newUser = User().apply {
            this.login = data.login
            this.encryptedPassword = encryptedPassword
            this.type = data.type
        }

        return repository.save(newUser)
    }
}