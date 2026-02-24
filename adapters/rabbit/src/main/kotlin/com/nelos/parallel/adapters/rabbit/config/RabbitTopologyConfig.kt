package com.nelos.parallel.adapters.rabbit.config

import com.nelos.parallel.adapters.rabbit.RabbitConstants
import org.springframework.amqp.core.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares AMQP topology: exchanges, queues, and bindings for test task routing.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Configuration
class RabbitTopologyConfig {

    @Bean
    fun testDirectExchange(): DirectExchange =
        DirectExchange(RabbitConstants.TEST_DIRECT_EXCHANGE, true, false)

    @Bean
    fun nodeFanoutExchange(): FanoutExchange =
        FanoutExchange(RabbitConstants.NODE_FANOUT_EXCHANGE, true, false)

    @Bean
    fun tasksCorrectnessQueue(): Queue =
        QueueBuilder.durable(RabbitConstants.TASKS_CORRECTNESS_QUEUE).build()

    @Bean
    fun tasksPerformanceQueue(): Queue =
        QueueBuilder.durable(RabbitConstants.TASKS_PERFORMANCE_QUEUE).build()

    @Bean
    fun resultsQueue(): Queue =
        QueueBuilder.durable(RabbitConstants.RESULTS_QUEUE).build()

    @Bean
    fun nodeEventsQueue(): Queue =
        QueueBuilder.durable(RabbitConstants.NODE_EVENTS_QUEUE).build()

    @Bean
    fun correctnessBinding(tasksCorrectnessQueue: Queue, testDirectExchange: DirectExchange): Binding =
        BindingBuilder.bind(tasksCorrectnessQueue).to(testDirectExchange)
            .with(RabbitConstants.ROUTING_KEY_CORRECTNESS)

    @Bean
    fun performanceBinding(tasksPerformanceQueue: Queue, testDirectExchange: DirectExchange): Binding =
        BindingBuilder.bind(tasksPerformanceQueue).to(testDirectExchange)
            .with(RabbitConstants.ROUTING_KEY_PERFORMANCE)

    @Bean
    fun resultsBinding(resultsQueue: Queue, testDirectExchange: DirectExchange): Binding =
        BindingBuilder.bind(resultsQueue).to(testDirectExchange)
            .with(RabbitConstants.ROUTING_KEY_RESULTS)

    @Bean
    fun nodeEventsBinding(nodeEventsQueue: Queue, nodeFanoutExchange: FanoutExchange): Binding =
        BindingBuilder.bind(nodeEventsQueue).to(nodeFanoutExchange)
}