package com.nelos.parallel.dev.view.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.NodeTransportManager
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.listener.TaskResultListenerRegistry
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import com.nelos.parallel.dev.view.vo.NodeView
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

/**
 * Test view service for RabbitMQ adapter - allows interacting with C-tests-runner nodes via AMQP.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.rabbitAdapterTestViewService", roles = [AppRole.ADMIN])
class RabbitAdapterTestViewService @Autowired constructor(
    nodeRegistry: NodeRegistry,
    objectMapper: ObjectMapper,
    listenerRegistry: TaskResultListenerRegistry,
    transportManager: NodeTransportManager,
    @Qualifier("prl.rabbitNodeAdapter") override val adapter: NodeAdapter,
) : AbstractAdapterTestViewService(nodeRegistry, objectMapper, listenerRegistry, transportManager) {

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
        private val LOG = LoggerFactory.getLogger(RabbitAdapterTestViewService::class.java)
    }
}
