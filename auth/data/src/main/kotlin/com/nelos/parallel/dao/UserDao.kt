package com.nelos.parallel.dao

import com.nelos.parallel.commons.dao.GenericDao
import com.nelos.parallel.entity.User

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface UserDao : GenericDao<User> {

    fun findByLogin(login: String): User?
}