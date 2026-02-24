package com.nelos.parallel.commons.service

import com.nelos.parallel.commons.entity.Entity

/**
 * Base service interface providing CRUD operations for entities.
 *
 * @param T entity type
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface Service<T : Entity> {

    /**
     * Returns all entities of type [T].
     */
    fun findAll(): List<T>

    /**
     * Persists or updates the given [entity] and returns the managed instance.
     */
    fun save(entity: T): T

    /**
     * Persists or updates a collection of [entities] and returns the managed instances.
     */
    fun save(entities: Collection<T>): List<T>

    /**
     * Removes the given [entity].
     */
    fun remove(entity: T)

    /**
     * Removes a collection of [entities].
     */
    fun remove(entities: Collection<T>)
}