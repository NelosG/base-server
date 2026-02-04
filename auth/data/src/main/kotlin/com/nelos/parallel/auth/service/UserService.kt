package com.nelos.parallel.auth.service

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.commons.service.GenericService

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface UserService : GenericService<User> {

    fun findByLogin(login: String): User?
}