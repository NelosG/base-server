package com.nelos.parallel.auth.service.impl

import com.nelos.parallel.auth.dao.ApiKeyDao
import com.nelos.parallel.auth.entity.ApiKey
import com.nelos.parallel.auth.service.ApiKeyService
import com.nelos.parallel.commons.service.impl.GenericServiceImpl
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.*

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.apiKeyService")
class ApiKeyServiceImpl : GenericServiceImpl<ApiKey, ApiKeyDao>(), ApiKeyService {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun validateKey(rawKey: String): Boolean {
        val hash = sha256(rawKey)
        val apiKey = invokeDaoMethod { it.findByKeyHash(hash) } ?: return false
        return apiKey.active
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun generateKey(name: String): Pair<ApiKey, String> {
        val rawKey = UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "")

        val apiKey = ApiKey().apply {
            this.keyHash = sha256(rawKey)
            this.keyPrefix = rawKey.substring(0, 8)
            this.name = name
            this.active = true
            this.createdAt = Instant.now()
        }

        val saved = invokeDaoMethod { it.save(apiKey) }
        return saved to rawKey
    }

    companion object {
        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
