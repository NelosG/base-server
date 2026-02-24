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
     * Removes an entity by its [id].
     */
    fun remove(id: Long)
}