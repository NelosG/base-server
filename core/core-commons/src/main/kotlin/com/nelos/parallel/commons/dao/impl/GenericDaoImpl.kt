package com.nelos.parallel.commons.dao.impl

import com.nelos.parallel.commons.dao.GenericDao
import com.nelos.parallel.commons.dao.exceptions.DaoException
import com.nelos.parallel.commons.entity.AbstractEntity
import com.nelos.parallel.commons.util.TerFunction
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Root
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
abstract class GenericDaoImpl<T : AbstractEntity> : DaoImpl<T>(), GenericDao<T> {

    override val defaultOrder = TerFunction<CriteriaBuilder, CriteriaQuery<T>, Root<T>, Order> { cb, _, root ->
        cb.asc(
            root.get<String>(AbstractEntity.ID)
        )
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findById(id: Long): T {
        return tryFindById(id) ?: throw DaoException(
            "Entity ${entityClass.simpleName} with id=$id was not found"
        ).also { LOG.warn(it.message) }
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun tryFindById(id: Long): T? {
        return entityManager.find(entityClass, id)
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun remove(entity: T) {
        remove(entity.id ?: error("Missing id in entity for removal"))
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun remove(id: Long) {
        val cb = entityManager.criteriaBuilder
        val cd = cb.createCriteriaDelete(entityClass)
        val root = cd.from(entityClass)
        cd.where(
            cb.equal(
                root.get<Any>(AbstractEntity.ID),
                cb.parameter(Long::class.java, AbstractEntity.ID)
            )
        )
        entityManager
            .createQuery(cd)
            .setParameter(AbstractEntity.ID, id)
            .executeUpdate()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GenericDaoImpl::class.java)
    }
}
