package com.nelos.parallel.auth.service.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.nelos.parallel.auth.dao.ApiKeyDao
import com.nelos.parallel.auth.entity.ApiKey
import com.nelos.parallel.auth.service.ApiKeyService
import com.nelos.parallel.commons.service.TxAfterCommit
import com.nelos.parallel.commons.service.impl.GenericServiceImpl
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.apiKeyService")
class ApiKeyServiceImpl : GenericServiceImpl<ApiKey, ApiKeyDao>(), ApiKeyService {

    /**
     * In-process validation cache keyed by **SHA-256(rawKey)** - never by the
     * raw secret itself. Storing only hashes means a heap dump or debugger
     * attachment cannot recover working API keys (they would have to brute-force
     * SHA-256, which is computationally infeasible). The hash here is the same
     * value already stored in `prl_api_key.key_hash`, so the cache contents are
     * no more sensitive than the DB column.
     *
     * Only positive results (`true`) are stored. Caching negatives would let an
     * attacker spamming invalid keys flood the cache and evict legitimate
     * entries, forcing DB lookups for every real request. Misses always hit DB.
     *
     * Eager invalidation fires **after** the surrounding transaction commits
     * (via [TxAfterCommit]) so that a rollback never clears the cache for an
     * unchanged DB row, and a concurrent validateKey cannot re-populate the
     * cache with the about-to-be-stale pre-commit value.
     */
    private val validationCache: Cache<String, Boolean> = Caffeine.newBuilder()
        .maximumSize(256)
        .expireAfterWrite(Duration.ofMinutes(5))
        .build()

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun validateKey(rawKey: String): Boolean {
        val hash = sha256(rawKey)
        validationCache.getIfPresent(hash)?.let { return it }
        val apiKey = invokeDaoMethod { it.findByKeyHash(hash) }
        val result = apiKey?.active == true
        if (result) {
            // Cache only positives; see KDoc on [validationCache] for rationale.
            validationCache.put(hash, true)
        }
        return result
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
        // Defensive: if some prior validateKey() somehow cached this hash as
        // true (impossible in practice given the 256-bit keyspace, but cheap to
        // be safe). After-commit so a rollback leaves cache alone.
        val hash = apiKey.keyHash ?: error("hash not set")
        TxAfterCommit.runAfterCommit { validationCache.invalidate(hash) }
        return saved to rawKey
    }

    override fun save(entity: ApiKey): ApiKey {
        // `active` toggle flows through here. Invalidate after commit so a
        // rollback doesn't clear the cache for an unchanged DB row, and a
        // concurrent validateKey can't re-cache the pre-commit value.
        val saved = super.save(entity)
        // Targeted invalidate: we know exactly which hash this row owns, so
        // we don't need to drop every cached positive on every save.
        saved.keyHash?.let { hash ->
            TxAfterCommit.runAfterCommit { validationCache.invalidate(hash) }
        }
        return saved
    }

    override fun remove(id: Long) {
        super.remove(id)
        TxAfterCommit.runAfterCommit { validationCache.invalidateAll() }
    }

    companion object {
        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
