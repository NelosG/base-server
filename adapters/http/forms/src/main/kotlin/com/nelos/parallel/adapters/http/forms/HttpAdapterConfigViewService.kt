package com.nelos.parallel.adapters.http.forms

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.adapters.config.AbstractAdapterConfigViewService
import com.nelos.parallel.adapters.http.HttpNodeAdapter
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.NodeTransportManager
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * Admin-only HTTP adapter configuration ViewService - lists registered HTTP
 * nodes and exposes plugin / config management actions against each runner.
 * Submission, job-status and other diagnostic methods that lived on the old
 * "test" panel were removed - submissions go through the regular pipeline.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.httpAdapterConfigViewService", roles = [AppRole.ADMIN])
class HttpAdapterConfigViewService @Autowired constructor(
    nodeRegistry: NodeRegistry,
    objectMapper: ObjectMapper,
    transportManager: NodeTransportManager,
    override val adapter: HttpNodeAdapter,
) : AbstractAdapterConfigViewService(nodeRegistry, objectMapper, transportManager) {

    override val transportType: TransportType = TransportType.HTTP

    override val log: Logger = LOG

    companion object {
        private val LOG = LoggerFactory.getLogger(HttpAdapterConfigViewService::class.java)
    }
}
