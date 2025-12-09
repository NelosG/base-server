package com.nelos.parallel.core.dao

import com.nelos.parallel.core.entity.AbstractEntity

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface GenericDao<T : AbstractEntity> : Dao<T> {

    fun findById(id: Long): T

    fun tryFindById(id: Long): T?

    fun remove(id: Long)
}