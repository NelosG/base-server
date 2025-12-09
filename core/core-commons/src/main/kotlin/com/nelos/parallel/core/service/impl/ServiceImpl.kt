package com.nelos.parallel.core.service.impl

import com.nelos.parallel.core.dao.Dao
import com.nelos.parallel.core.entity.Entity
import com.nelos.parallel.core.service.Service
import com.nelos.parallel.core.service.exceptions.ServiceException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
abstract class ServiceImpl<T : Entity, DAO : Dao<T>> :
    Service<T> {

    @Autowired
    protected lateinit var dao: DAO

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findAll(): List<T> {
        return invokeDaoMethod { it.findAll() }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun save(entity: T): T {
        return invokeDaoMethod { it.save(entity) }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun save(entities: Collection<T>): List<T> {
        return invokeDaoMethod { it.save(entities) }
    }


    @Transactional(propagation = Propagation.REQUIRED)
    override fun remove(entities: Collection<T>) {
        return entities.forEach(::remove)
    }


    fun <R> invokeDaoMethod(function: Function1<DAO, R>): R {
        return try {
            function.invoke(dao)
        } catch (e: Exception) {
            throw createDataAccessError(e)
        }
    }

    private fun createDataAccessError(e: Exception): ServiceException {
        //TODO: add logs
        return ServiceException("Failed to access DB: ${e.message}", e)
    }
}
