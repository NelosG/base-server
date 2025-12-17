package com.nelos.parallel.commons.service

import com.nelos.parallel.commons.entity.Entity


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