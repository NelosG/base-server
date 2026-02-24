package com.nelos.parallel.commons.dao

import com.nelos.parallel.commons.entity.Entity
import com.nelos.parallel.commons.util.TerFunction
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import java.util.function.Supplier

/**
 * Base DAO interface providing CRUD operations for entities.
 *
 * @param T entity type
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface Dao<T : Entity> {

    /**
     * Returns all entities of type [T].
     */
    fun findAll(): List<T>

    /**
     * Returns entities matching the given [conditions] with a maximum [limit].
     *
     * @param limit maximum number of results
     * @param conditions JPA criteria predicate, or `null` for no filtering
     */
    fun findByCondition(
        limit: Int,
        conditions: TerFunction<CriteriaBuilder, CriteriaQuery<T>, Root<T>, Predicate>?
    ): List<T>

    /**
     * Returns entities matching the given [conditions] with the default limit.
     *
     * @param conditions JPA criteria predicate
     */
    fun findByCondition(conditions: TerFunction<CriteriaBuilder, CriteriaQuery<T>, Root<T>, Predicate>): List<T>

    /**
     * Persists or merges the given [entity] and returns the managed instance.
     */
    fun save(entity: T): T

    /**
     * Persists or merges a collection of [entities] and returns the managed instances.
     */
    fun save(entities: Collection<T>): List<T>

    /**
     * Removes the given [entity] from the persistence context.
     */
    fun remove(entity: T)

    /**
     * Executes the [supplier] within a manual transaction and returns the result.
     */
    fun <K> runInTransaction(supplier: Supplier<K>): K

    /**
     * The JPA entity class managed by this DAO.
     */
    val entityClass: Class<T>
}
