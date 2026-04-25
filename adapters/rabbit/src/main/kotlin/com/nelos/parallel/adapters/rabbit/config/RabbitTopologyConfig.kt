package com.nelos.parallel.adapters.rabbit.config

import com.nelos.parallel.adapters.rabbit.RabbitConstants
import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
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
    fun nodeControlDirectExchange(): DirectExchange =
        DirectExchange(RabbitConstants.NODE_CONTROL_EXCHANGE, true, false)

    @Bean
    fun tasksQueue(): Queue =
        QueueBuilder.durable(RabbitConstants.TASKS_QUEUE).build()

    @Bean
    fun resultsQueue(): Queue =
        QueueBuilder.durable(RabbitConstants.RESULTS_QUEUE).build()

    @Bean
    fun progressQueue(): Queue =
        QueueBuilder.durable(RabbitConstants.PROGRESS_QUEUE).build()

    @Bean
    fun correctnessBinding(tasksQueue: Queue, testDirectExchange: DirectExchange): Binding =
        BindingBuilder.bind(tasksQueue).to(testDirectExchange)
            .with(RabbitConstants.ROUTING_KEY_CORRECTNESS)

    @Bean
    fun performanceBinding(tasksQueue: Queue, testDirectExchange: DirectExchange): Binding =
        BindingBuilder.bind(tasksQueue).to(testDirectExchange)
            .with(RabbitConstants.ROUTING_KEY_PERFORMANCE)

    @Bean
    fun allBinding(tasksQueue: Queue, testDirectExchange: DirectExchange): Binding =
        BindingBuilder.bind(tasksQueue).to(testDirectExchange)
            .with(RabbitConstants.ROUTING_KEY_ALL)

    @Bean
    fun resultsBinding(resultsQueue: Queue, testDirectExchange: DirectExchange): Binding =
        BindingBuilder.bind(resultsQueue).to(testDirectExchange)
            .with(RabbitConstants.ROUTING_KEY_RESULTS)

    @Bean
    fun progressBinding(progressQueue: Queue, testDirectExchange: DirectExchange): Binding =
        BindingBuilder.bind(progressQueue).to(testDirectExchange)
            .with(RabbitConstants.ROUTING_KEY_PROGRESS)

    /**
     * Dedicated RabbitTemplate for *control RPCs* (statusRequest, queueStatus,
     * cancelJob, etc.) with a short reply timeout. The default Spring template
     * uses `spring.rabbitmq.template.reply-timeout=30000` which is sized for
     * `submitTask` (engine has to queue the job before acking), but health
     * checks against dead nodes shouldn't burn 30s of Tomcat thread time -
     * 5s is plenty for a live node to respond.
     */
    @Bean("prl.controlRabbitTemplate")
    fun controlRabbitTemplate(connectionFactory: ConnectionFactory): RabbitTemplate =
        RabbitTemplate(connectionFactory).apply {
            setReplyTimeout(CONTROL_REPLY_TIMEOUT_MS)
        }

    companion object {
        private const val CONTROL_REPLY_TIMEOUT_MS = 5_000L
    }
}