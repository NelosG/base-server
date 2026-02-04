package com.nelos.parallel.auth.dao

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.commons.dao.GenericDao

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface UserDao : GenericDao<User> {

    fun findByLogin(login: String): User?
}