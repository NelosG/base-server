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

    // Queues
    const val TASKS_CORRECTNESS_QUEUE = "test.tasks.correctness"
    const val TASKS_PERFORMANCE_QUEUE = "test.tasks.performance"
    const val RESULTS_QUEUE = "test.results"
    const val NODE_EVENTS_QUEUE = "node.events"

    // Routing keys
    const val ROUTING_KEY_CORRECTNESS = "correctness"
    const val ROUTING_KEY_PERFORMANCE = "performance"
    const val ROUTING_KEY_RESULTS = "results"
}
