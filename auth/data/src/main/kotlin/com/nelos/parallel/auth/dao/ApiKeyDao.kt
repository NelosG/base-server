package com.nelos.parallel.auth.dao

import com.nelos.parallel.auth.entity.ApiKey
import com.nelos.parallel.commons.dao.GenericDao

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface ApiKeyDao : GenericDao<ApiKey> {

    fun findByKeyHash(hash: String): ApiKey?
}
