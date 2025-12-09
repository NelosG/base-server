package com.nelos.parallel.core.dao

import com.nelos.parallel.core.entity.CodeEntity


/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface CodeDao<T : CodeEntity> : GenericDao<T> {

    fun findByCode(code: String): T?

    fun findByName(name: String): List<T>
}
