package com.nelos.parallel.dev.view.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.adapters.rabbit.RabbitNodeAdapter
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.NodeTransportManager
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.listener.TaskResultListenerRegistry
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * Test view service for RabbitMQ adapter — allows interacting with C-tests-runner nodes via AMQP.
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

    companion object {
        private val LOG = LoggerFactory.getLogger(RabbitAdapterTestViewService::class.java)
    }
}
