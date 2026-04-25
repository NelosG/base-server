package com.nelos.parallel.dev.view.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.adapters.rabbit.RabbitNodeAdapter
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
    override val adapter: RabbitNodeAdapter,
) : AbstractAdapterTestViewService(nodeRegistry, objectMapper, listenerRegistry, transportManager) {

    override val transportType: TransportType = TransportType.AMQP

    override val log: Logger = LOG

    /**
     * AMQP runners do not push lifecycle events - they reply to a `statusRequest`
     * fanout broadcast. Refresh therefore performs an on-demand discovery sweep:
     * registered AMQP nodes that don't reply within the timeout are evicted, and
     * any new responders are persisted into the registry so the pipeline can
     * dispatch to them.
     */
    override fun refreshAndPruneNodes(): List<NodeView> {
        val discovered = adapter.discoverNodes(DISCOVERY_TIMEOUT_MS)
        val responding = discovered.map { it.nodeId }.toSet()
        log.info("AMQP discovery: {} node(s) replied", discovered.size)

        nodeRegistry.findByTransport(TransportType.AMQP)
            .filter { it.nodeId !in responding }
            .forEach { stale ->
                transportManager.handleHealthCheckFailure(stale.nodeId, TransportType.AMQP)
                log.info("AMQP node did not respond to discovery: {}", stale.nodeId)
            }

        discovered.forEach { nodeRegistry.register(it) }
        return getNodes()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RabbitAdapterTestViewService::class.java)
        private const val DISCOVERY_TIMEOUT_MS = 2000L
    }
}
