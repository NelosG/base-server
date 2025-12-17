package com.nelos.parallel.commons.service

import com.nelos.parallel.commons.entity.CodeEntity

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface CodeService<T : CodeEntity> : GenericService<T> {

    fun findByCode(code: String): T?

    fun findByName(name: String): List<T>
}
