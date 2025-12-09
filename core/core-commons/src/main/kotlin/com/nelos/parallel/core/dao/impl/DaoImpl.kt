package com.nelos.parallel.core.dao.impl

import com.nelos.parallel.core.dao.Dao
import com.nelos.parallel.core.entity.Entity
import com.nelos.parallel.core.util.TerFunction
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

    override val entityClass: Class<T> = run {
        val genericSuperclass = javaClass.genericSuperclass as ParameterizedType
        val genericParameters = genericSuperclass.actualTypeArguments
        @Suppress("UNCHECKED_CAST")
        genericParameters[0] as Class<T>
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findAll(): List<T> {
        val cb = entityManager.criteriaBuilder
        val cq = cb.createQuery(entityClass)
        val root = cq.from(entityClass)

        cq.select(root)
        val order = defaultOrder?.apply(cb, cq, root)

        if (order != null) {
            cq.orderBy(order)
        }
        val query = entityManager.createQuery(
            cq
        )
        return query.resultList//TODO: rewrite
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

        if (conditions != null) {
            cq.where(
                conditions.apply(cb, cq, root)
            )
        }
        val query = entityManager.createQuery(cq)
        query.maxResults = min(limit, QUERY_LIMIT)
        return query.resultList
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
        this.entityManager.transaction.begin()
        val res = supplier.get()
        this.entityManager.transaction.commit()
        return res
    }

    companion object {
        const val QUERY_LIMIT = 10000
    }
}
