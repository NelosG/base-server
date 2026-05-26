package com.nelos.parallel.adapters.rabbit.forms

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.adapters.config.AbstractAdapterConfigViewService
import com.nelos.parallel.adapters.config.vo.NodeView
import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.NodeTransportManager
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

/**
 * Admin-only AMQP adapter configuration ViewService - lists registered AMQP
 * nodes and exposes plugin / config management actions against each runner.
 * Submission, job-status and other diagnostic methods that lived on the old
 * "test" panel were removed - submissions go through the regular pipeline.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.rabbitAdapterConfigViewService", roles = [AppRole.ADMIN])
class RabbitAdapterConfigViewService @Autowired constructor(
    nodeRegistry: NodeRegistry,
    objectMapper: ObjectMapper,
    transportManager: NodeTransportManager,
    @Qualifier("prl.rabbitNodeAdapter") override val adapter: NodeAdapter,
) : AbstractAdapterConfigViewService(nodeRegistry, objectMapper, transportManager) {

    override val transportType: TransportType = TransportType.AMQP

    override val log: Logger = LOG

    /**
     * AMQP runners don't push lifecycle events - discovery is on-demand via a
     * `statusRequest` fanout. Delegated to [NodeTransportManager.discoverAndRefresh]
     * so the pipeline submit path can trigger the same flow when its registry
     * snapshot has no live AMQP nodes.
     */
    override fun refreshAndPruneNodes(): List<NodeView> {
        transportManager.discoverAndRefresh(TransportType.AMQP)
        return getNodes()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RabbitAdapterConfigViewService::class.java)
    }
}
