package com.nelos.parallel.auth.service.impl

import com.nelos.parallel.auth.dao.ApiKeyDao
import com.nelos.parallel.auth.entity.ApiKey
import com.nelos.parallel.commons.service.impl.ServiceImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class ApiKeyServiceImplTest {

    private lateinit var dao: ApiKeyDao
    private lateinit var service: ApiKeyServiceImpl

    @BeforeEach
    fun setUp() {
        dao = mock()
        service = ApiKeyServiceImpl()
        injectDao(service, dao)
    }

    private fun injectDao(target: Any, dao: ApiKeyDao) {
        val field = ServiceImpl::class.java.getDeclaredField("dao")
        field.isAccessible = true
        field.set(target, dao)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun activeKey(hash: String) = ApiKey().apply {
        keyHash = hash
        active = true
    }

    // --- validateKey ---------------------------------------------------

    @Test
    fun `valid raw key returns true and is cached for the next call`() {
        val raw = "raw-key-1"
        val hash = sha256(raw)
        whenever(dao.findByKeyHash(hash)).thenReturn(activeKey(hash))

        assertTrue(service.validateKey(raw))
        assertTrue(service.validateKey(raw))

        // Second call must NOT hit the DAO again - that's the whole point of the cache.
        verify(dao, times(1)).findByKeyHash(hash)
    }

    @Test
    fun `inactive key in db returns false and is NOT cached`() {
        val raw = "raw-key-2"
        val hash = sha256(raw)
        whenever(dao.findByKeyHash(hash)).thenReturn(
            ApiKey().apply { keyHash = hash; active = false },
        )

        assertFalse(service.validateKey(raw))
        assertFalse(service.validateKey(raw))

        // Negative results must NOT be cached (see ApiKeyServiceImpl KDoc).
        verify(dao, times(2)).findByKeyHash(hash)
    }

    @Test
    fun `unknown key returns false and is NOT cached`() {
        val raw = "raw-key-3"
        val hash = sha256(raw)
        whenever(dao.findByKeyHash(hash)).thenReturn(null)

        assertFalse(service.validateKey(raw))
        assertFalse(service.validateKey(raw))

        verify(dao, times(2)).findByKeyHash(hash)
    }

    // --- generateKey ----------------------------------------------------

    @Test
    fun `generateKey returns a raw 64-hex-char value and a stored entity with prefix and hash`() {
        whenever(dao.save(any<ApiKey>())).thenAnswer { it.arguments[0] }

        val (entity, raw) = service.generateKey(name = "ci-job-1")

        // Raw key is 2 x UUID without dashes - 64 hex chars.
        assertEquals(64, raw.length)
        assertTrue(raw.all { it in '0'..'9' || it in 'a'..'f' })

        assertEquals(raw.substring(0, 8), entity.keyPrefix)
        assertEquals(sha256(raw), entity.keyHash)
        assertEquals("ci-job-1", entity.name)
        assertTrue(entity.active)
    }

    @Test
    fun `validateKey after generateKey accepts the freshly-issued raw key`() {
        whenever(dao.save(any<ApiKey>())).thenAnswer { it.arguments[0] }

        val (entity, raw) = service.generateKey(name = "n")
        // Now simulate the DAO returning the saved key on lookup.
        whenever(dao.findByKeyHash(entity.keyHash!!)).thenReturn(entity)

        assertTrue(service.validateKey(raw))
    }

    // --- cache invalidation ---------------------------------------------

    @Test
    fun `save() invalidates the cache so the next validate re-hits the DB`() {
        val raw = "raw-key-4"
        val hash = sha256(raw)
        val key = activeKey(hash)
        whenever(dao.findByKeyHash(hash)).thenReturn(key)
        whenever(dao.save(any<ApiKey>())).thenReturn(key)

        // Prime the cache with a valid lookup.
        service.validateKey(raw)
        // Toggle and save.
        service.save(key)
        // Next lookup must hit DAO again.
        service.validateKey(raw)

        verify(dao, times(2)).findByKeyHash(hash)
    }

    @Test
    fun `remove() invalidates the cache`() {
        val raw = "raw-key-5"
        val hash = sha256(raw)
        whenever(dao.findByKeyHash(hash)).thenReturn(activeKey(hash))
        whenever(dao.tryFindById(any())).thenReturn(activeKey(hash))

        service.validateKey(raw)
        service.remove(7L)
        service.validateKey(raw)

        verify(dao, times(2)).findByKeyHash(hash)
    }
}
