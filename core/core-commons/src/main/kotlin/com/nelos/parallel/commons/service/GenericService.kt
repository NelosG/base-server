package com.nelos.parallel.commons.service

import com.nelos.parallel.commons.entity.AbstractEntity
import com.nelos.parallel.commons.service.exceptions.ServiceException
import java.util.stream.Stream

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
     * Returns a lazily-loaded [Stream] of all entities, fetched in batches of [batchSize].
     */
    fun findAsStream(batchSize: Int): Stream<T>

    /**
     * Removes entities by their [ids].
     */
    fun remove(ids: List<Long>)

    /**
     * Removes an entity by its [id].
     */
    fun remove(id: Long)
}
