package com.nelos.parallel.core.service

import com.nelos.parallel.core.entity.AbstractEntity
import java.util.stream.Stream

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface GenericService<T : AbstractEntity> : Service<T> {

    fun findById(id: Long): T

    fun tryFindById(id: Long): T?

    fun findAsStream(batchSize: Int): Stream<T>

    fun remove(ids: List<Long>)

    fun remove(id: Long)
}
