package com.nelos.parallel.dev.view.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.adapters.http.HttpNodeAdapter
import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.view.service.ViewService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

/**
 * Test view service for HTTP adapter — allows interacting with C-tests-runner nodes via HTTP.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.httpAdapterTestViewService")
class HttpAdapterTestViewService @Autowired constructor(
    nodeRegistry: NodeRegistry,
    objectMapper: ObjectMapper,
    private val httpNodeAdapter: HttpNodeAdapter,
) : AbstractAdapterTestViewService(nodeRegistry, objectMapper) {

    override val transportType: TransportType = TransportType.HTTP

    override val log: Logger = LOG

    override val adapter: NodeAdapter = httpNodeAdapter

    override fun resolveCallbackUrl(): String =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path(CALLBACK_URL)
            .toUriString()

    companion object {
        private val LOG = LoggerFactory.getLogger(HttpAdapterTestViewService::class.java)
    }
}
