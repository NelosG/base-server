package com.nelos.parallel.dao.impl

import com.nelos.parallel.commons.dao.impl.GenericDaoImpl
import com.nelos.parallel.dao.UserDao
import com.nelos.parallel.entity.User
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Repository("prl.userDao")
class UserDaoImpl : GenericDaoImpl<User>(), UserDao {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByLogin(login: String): User? {
        return findByCondition { cb, _, root ->
            cb.equal(root.get<String>(User::login.name), login)
        }.firstOrNull()
    }
}