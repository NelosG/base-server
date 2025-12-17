package com.nelos.parallel.commons.dao

import com.nelos.parallel.commons.entity.CodeEntity


/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface CodeDao<T : CodeEntity> : GenericDao<T> {

    fun findByCode(code: String): T?

    fun findByName(name: String): List<T>
}
