package com.nelos.parallel.core.service

import com.nelos.parallel.core.entity.CodeEntity

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface CodeService<T : CodeEntity> : GenericService<T> {

    fun findByCode(code: String): T?

    fun findByName(name: String): List<T>
}
