package com.nelos.parallel.auth.view.service

import com.nelos.parallel.auth.entity.ApiKey
import com.nelos.parallel.auth.service.ApiKeyService
import com.nelos.parallel.auth.view.vo.CreateApiKeyRequest
import com.nelos.parallel.auth.view.vo.DeleteApiKeyRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import kotlin.test.assertEquals

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class ApiKeyViewServiceTest {

    private val apiKeyService: ApiKeyService = mock()
    private val service = ApiKeyViewService(apiKeyService)

    private fun apiKey(id: Long? = 1L, prefix: String = "abcd", name: String = "ci", active: Boolean = true): ApiKey =
        ApiKey().apply {
            if (id != null) this.id = id
            this.keyPrefix = prefix
            this.name = name
            this.active = active
            this.createdAt = Instant.parse("2026-01-01T00:00:00Z")
        }

    @Test
    fun `getApiKeys errors when the persistence layer hands back a row with no id`() {
        // An ApiKey without an id is a logical impossibility post-save, but the
        // view service guards against it instead of silently emitting 0.
        whenever(apiKeyService.findAll()).thenReturn(listOf(apiKey(id = null)))

        assertThrows<IllegalStateException> { service.getApiKeys() }
    }

    @Test
    fun `createApiKey trims whitespace and forwards the cleaned name to the service`() {
        val saved = apiKey(99L, "ZZZZ", "ci")
        whenever(apiKeyService.generateKey("ci")).thenReturn(saved to "raw-secret-value")

        val resp = service.createApiKey(CreateApiKeyRequest(name = "  ci  "))

        assertEquals(99L, resp.id)
        assertEquals("ci", resp.name)
        assertEquals("raw-secret-value", resp.rawKey)
        verify(apiKeyService).generateKey("ci")
    }

    @Test
    fun `createApiKey rejects a blank name`() {
        assertThrows<IllegalArgumentException> {
            service.createApiKey(CreateApiKeyRequest(name = "   "))
        }
    }

    @Test
    fun `createApiKey rejects an overlong name`() {
        // 101 chars - the limit is 100.
        val tooLong = "x".repeat(101)
        assertThrows<IllegalArgumentException> {
            service.createApiKey(CreateApiKeyRequest(name = tooLong))
        }
    }

    @Test
    fun `createApiKey errors when the service hands back an entity with no id`() {
        whenever(apiKeyService.generateKey("ci")).thenReturn(apiKey(id = null) to "raw")
        assertThrows<IllegalStateException> { service.createApiKey(CreateApiKeyRequest(name = "ci")) }
    }

    @Test
    fun `deleteApiKey forwards the id to the persistence service`() {
        service.deleteApiKey(DeleteApiKeyRequest(id = 7L))
        verify(apiKeyService).remove(7L)
    }
}
