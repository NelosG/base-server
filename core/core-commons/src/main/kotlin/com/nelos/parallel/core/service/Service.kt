package com.nelos.parallel.core.service

import com.nelos.parallel.core.entity.Entity


/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface Service<T : Entity> {

    fun findAll(): List<T>

    fun save(entity: T): T

    fun save(entities: Collection<T>): List<T>

    fun remove(entity: T)

    fun remove(entities: Collection<T>)
}