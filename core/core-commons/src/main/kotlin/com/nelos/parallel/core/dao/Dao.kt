package com.nelos.parallel.core.dao

import com.nelos.parallel.core.entity.Entity
import com.nelos.parallel.core.util.TerFunction
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import java.util.function.Supplier

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface Dao<T : Entity> {

    fun findAll(): List<T>

    fun findByCondition(
        limit: Int,
        conditions: TerFunction<CriteriaBuilder, CriteriaQuery<T>, Root<T>, Predicate>?
    ): List<T>

    fun findByCondition(conditions: TerFunction<CriteriaBuilder, CriteriaQuery<T>, Root<T>, Predicate>): List<T>

    fun save(entity: T): T

    fun save(entities: Collection<T>): List<T>

    fun remove(entity: T)

    fun <K> runInTransaction(supplier: Supplier<K>): K

    val entityClass: Class<T>
}
