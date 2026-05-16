package com.nelos.parallel.commons.dao

import com.nelos.parallel.commons.dao.exceptions.DaoException
import com.nelos.parallel.commons.entity.AbstractEntity

/**
 * DAO interface for entities with a generated numeric ID.
 *
 * @param T entity type extending [AbstractEntity]
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface GenericDao<T : AbstractEntity> : Dao<T> {

    /**
     * Finds an entity by its [id].
     *
     * @throws [DaoException] if not found
     */
    fun findById(id: Long): T

    /**
     * Finds an entity by its [id], returning `null` if not found.
     */
    fun tryFindById(id: Long): T?

    /**
     * Same as [tryFindById] but acquires a `SELECT ... FOR UPDATE` row lock,
     * blocking other writers on the same row until the surrounding transaction
     * commits. Use in read-modify-write paths to serialize concurrent updates
     * on the same entity. Must run inside a writable transaction.
     */
    fun tryFindByIdForUpdate(id: Long): T?

    /**
     * Removes an entity by its [id].
     */
    fun remove(id: Long)
}