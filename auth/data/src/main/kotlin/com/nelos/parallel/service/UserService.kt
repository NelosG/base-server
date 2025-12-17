package com.nelos.parallel.service

import com.nelos.parallel.commons.service.GenericService
import com.nelos.parallel.entity.User

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface UserService : GenericService<User> {

    fun findByLogin(login: String): User?
}