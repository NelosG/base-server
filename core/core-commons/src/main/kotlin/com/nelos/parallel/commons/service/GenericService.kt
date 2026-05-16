package com.nelos.parallel.commons.service

import com.nelos.parallel.commons.entity.AbstractEntity
import com.nelos.parallel.commons.service.exceptions.ServiceException

/**
 * Service interface for entities with a generated numeric ID.
 *
 * @param T entity type extending [AbstractEntity]
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface GenericService<T : AbstractEntity> : Service<T> {

    /**
     * Finds an entity by its [id].
     *
     * @throws [ServiceException] if not found
     */
    fun findById(id: Long): T

    /**
     * Finds an entity by its [id], returning `null` if not found.
     */
    fun tryFindById(id: Long): T?

    /**
     * Same as [tryFindById] but acquires a `SELECT ... FOR UPDATE` row lock.
     * Use in read-modify-write paths to serialize concurrent updates on the
     * same entity. Must run inside a writable transaction.
     */
    fun tryFindByIdForUpdate(id: Long): T?

    /**
     * Removes entities by their [ids].
     */
    fun remove(ids: List<Long>)

    /**
     * Removes an entity by its [id].
     */
    fun remove(id: Long)
}
