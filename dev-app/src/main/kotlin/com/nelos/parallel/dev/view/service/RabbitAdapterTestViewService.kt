package com.nelos.parallel.dev.view.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.adapters.rabbit.RabbitNodeAdapter
import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.enums.TransportType
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
@ViewService("prl.rabbitAdapterTestViewService")
class RabbitAdapterTestViewService @Autowired constructor(
    nodeRegistry: NodeRegistry,
    objectMapper: ObjectMapper,
    private val rabbitNodeAdapter: RabbitNodeAdapter,
) : AbstractAdapterTestViewService(nodeRegistry, objectMapper) {

    override val transportType: TransportType = TransportType.AMQP

    override val log: Logger = LOG

    override val adapter: NodeAdapter = rabbitNodeAdapter

    companion object {
        private val LOG = LoggerFactory.getLogger(RabbitAdapterTestViewService::class.java)
    }
}
