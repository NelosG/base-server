package com.nelos.parallel.commons.service.impl

import com.nelos.parallel.commons.dao.GenericDao
import com.nelos.parallel.commons.entity.AbstractEntity
import com.nelos.parallel.commons.service.GenericService
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
abstract class GenericServiceImpl<T : AbstractEntity, DAO : GenericDao<T>> :
    ServiceImpl<T, DAO>(), GenericService<T> {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findById(id: Long): T {
        return invokeDaoMethod { it.findById(id) }
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun tryFindById(id: Long): T? {
        return invokeDaoMethod { it.tryFindById(id) }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun remove(ids: List<Long>) {
        ids.forEach(::remove)
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun remove(id: Long) {
        invokeDaoMethod { it.remove(id) }
    }
}
