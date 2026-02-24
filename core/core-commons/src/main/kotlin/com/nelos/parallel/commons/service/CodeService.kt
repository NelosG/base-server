package com.nelos.parallel.commons.service

import com.nelos.parallel.commons.entity.CodeEntity

/**
 * Service interface for [CodeEntity] types, adding lookup by code and name.
 *
 * @param T entity type extending [CodeEntity]
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface CodeService<T : CodeEntity> : GenericService<T> {

    /**
     * Finds a single entity by its unique [code], or `null` if not found.
     */
    fun findByCode(code: String): T?

    /**
     * Finds all entities matching the given [name].
     */
    fun findByName(name: String): List<T>
}
