package com.nelos.parallel.service.impl

import com.nelos.parallel.commons.service.impl.GenericServiceImpl
import com.nelos.parallel.dao.UserDao
import com.nelos.parallel.entity.User
import com.nelos.parallel.service.UserService
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