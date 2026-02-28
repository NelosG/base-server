package com.nelos.parallel.adapters.rabbit

/**
 * AMQP topology constants for RabbitMQ communication with test-runner nodes.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
object RabbitConstants {

    // Exchanges
    const val TEST_DIRECT_EXCHANGE = "test.direct"
    const val NODE_FANOUT_EXCHANGE = "node.fanout"
    const val NODE_CONTROL_EXCHANGE = "node.control.direct"

    // Queues
    const val TASKS_QUEUE = "test.tasks"
    const val RESULTS_QUEUE = "test.results"
    const val NODE_EVENTS_QUEUE = "node.events"

    // Routing keys
    const val ROUTING_KEY_CORRECTNESS = "correctness"
    const val ROUTING_KEY_PERFORMANCE = "performance"
    const val ROUTING_KEY_ALL = "all"
    const val ROUTING_KEY_RESULTS = "results"
}
