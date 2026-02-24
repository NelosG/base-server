package com.nelos.parallel.commons.dao.impl

import com.nelos.parallel.commons.dao.Dao
import com.nelos.parallel.commons.entity.Entity
import com.nelos.parallel.commons.util.TerFunction
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.criteria.*
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.lang.reflect.ParameterizedType
import java.util.function.Supplier
import kotlin.math.min

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
abstract class DaoImpl<T : Entity> : Dao<T> {

    @set:PersistenceContext(unitName = "parallelSystemUnit")
    protected open lateinit var entityManager: EntityManager

    protected open val defaultOrder: TerFunction<CriteriaBuilder, CriteriaQuery<T>, Root<T>, Order>? = null

    @Suppress("UNCHECKED_CAST")
    override val entityClass: Class<T> =
        (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0] as Class<T>

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findAll(): List<T> {
        val cb = entityManager.criteriaBuilder
        val cq = cb.createQuery(entityClass)
        val root = cq.from(entityClass)

        cq.select(root)
        defaultOrder?.apply(cb, cq, root)?.let { cq.orderBy(it) }

        return entityManager.createQuery(cq).resultList
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByCondition(conditions: TerFunction<CriteriaBuilder, CriteriaQuery<T>, Root<T>, Predicate>): List<T> {
        return findByCondition(QUERY_LIMIT, conditions)
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByCondition(
        limit: Int,
        conditions: TerFunction<CriteriaBuilder, CriteriaQuery<T>, Root<T>, Predicate>?
    ): List<T> {
        val cb = entityManager.criteriaBuilder
        val cq = cb.createQuery(entityClass)
        val root = cq.from(entityClass)

        conditions?.apply(cb, cq, root)?.let { cq.where(it) }

        return entityManager.createQuery(cq)
            .apply { maxResults = min(limit, QUERY_LIMIT) }
            .resultList
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun save(entity: T): T {
        return entityManager.merge(entity)
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun save(entities: Collection<T>): List<T> {
        return entities.map(::save)
    }

    override fun <K> runInTransaction(supplier: Supplier<K>): K {
        entityManager.transaction.begin()
        val result = supplier.get()
        entityManager.transaction.commit()
        return result
    }

    companion object {
        const val QUERY_LIMIT = 10000
    }
}
