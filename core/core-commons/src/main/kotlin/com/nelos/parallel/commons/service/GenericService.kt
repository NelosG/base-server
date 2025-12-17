package com.nelos.parallel.commons.service

import com.nelos.parallel.commons.entity.AbstractEntity
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
