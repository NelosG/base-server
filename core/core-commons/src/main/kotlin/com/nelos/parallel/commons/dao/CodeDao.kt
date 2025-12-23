package com.nelos.parallel.commons.dao

import com.nelos.parallel.commons.entity.CodeEntity

/**
 * DAO interface for [CodeEntity] types, adding lookup by code and name.
 *
 * @param T entity type extending [CodeEntity]
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface CodeDao<T : CodeEntity> : GenericDao<T> {

    /**
     * Finds a single entity by its unique [code], or `null` if not found.
     */
    fun findByCode(code: String): T?

    /**
     * Finds all entities matching the given [name].
     */
    fun findByName(name: String): List<T>
}
