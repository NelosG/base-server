package com.nelos.parallel.auth.service

import com.nelos.parallel.auth.entity.ApiKey
import com.nelos.parallel.commons.service.GenericService

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface ApiKeyService : GenericService<ApiKey> {

    fun validateKey(rawKey: String): Boolean

    fun generateKey(name: String): Pair<ApiKey, String>
}
