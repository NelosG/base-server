package com.nelos.parallel.auth.view.service

import com.nelos.parallel.auth.service.ApiKeyService
import com.nelos.parallel.auth.view.vo.ApiKeyView
import com.nelos.parallel.auth.view.vo.CreateApiKeyRequest
import com.nelos.parallel.auth.view.vo.CreateApiKeyResponse
import com.nelos.parallel.auth.view.vo.DeleteApiKeyRequest
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService

/**
 * View service for managing API keys (admin only).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.apiKeyViewService", roles = [AppRole.ADMIN])
class ApiKeyViewService(private val apiKeyService: ApiKeyService) {

    fun getApiKeys(): List<ApiKeyView> {
        return apiKeyService.findAll().map { key ->
            ApiKeyView(
                id = key.id ?: error("API key id must not be null"),
                keyPrefix = key.keyPrefix ?: "",
                name = key.name ?: "",
                active = key.active,
                createdAt = key.createdAt
            )
        }
    }

    fun createApiKey(request: CreateApiKeyRequest): CreateApiKeyResponse {
        val name = request.name.trim()
        require(name.length in 1..100) { "Key name must be between 1 and 100 characters" }
        val (apiKey, rawKey) = apiKeyService.generateKey(name)
        return CreateApiKeyResponse(
            id = apiKey.id ?: error("API key id must not be null"),
            keyPrefix = apiKey.keyPrefix ?: "",
            name = apiKey.name ?: "",
            rawKey = rawKey
        )
    }

    fun deleteApiKey(request: DeleteApiKeyRequest) {
        apiKeyService.remove(request.id)
    }
}
