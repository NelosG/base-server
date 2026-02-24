package com.nelos.parallel.auth.service.impl

import com.nelos.parallel.auth.dao.UserDao
import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.commons.service.impl.GenericServiceImpl
import org.springframework.stereotype.Service

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.userService")
class UserServiceImpl : GenericServiceImpl<User, UserDao>(), UserService {

    override fun findByLogin(login: String): User? {
        return invokeDaoMethod { dao.findByLogin(login) }
    }
}