package com.nelos.parallel.load

import com.nelos.parallel.pipeline.commons.event.SubmissionTerminalEvent
import org.mockito.Mockito
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.event.EventListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Wires the load-test stub runner and the terminal-event listener into the
 * Spring context. Active only when @Imported by PipelineLoadTest.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@TestConfiguration
class PipelineLoadTestConfig {

    @Bean(destroyMethod = "shutdown")
    fun loadTestTaskRunner(
        @Value("\${loadtest.engine.task-duration-ms}") taskDurationMs: Long,
        @Value("\${loadtest.engine.callback-pool-size}") callbackPoolSize: Int,
    ): LoadTestTaskRunner = LoadTestTaskRunner(taskDurationMs, callbackPoolSize)

    @Bean
    fun inflightTracker(): InflightTracker = InflightTracker()

    @Bean
    fun terminalEventListener(tracker: InflightTracker): TerminalEventListener =
        TerminalEventListener(tracker)

    // Rabbit auto-config is disabled in application-loadtest.properties but
    // RabbitTopologyConfig (always-on @Configuration) still autowires a
    // ConnectionFactory for the control RabbitTemplate. Provide a mock so the
    // context bootstraps without a real broker. The Rabbit adapter is not used
    // in the load test - dispatch goes through the runner.
    @Bean
    @Primary
    fun mockRabbitConnectionFactory(): ConnectionFactory =
        Mockito.mock(ConnectionFactory::class.java)
}

/**
 * Shared per-test-run state: maps each submission's submit-time so the listener
 * can compute e2e latency, and routes the computed latency into the LoadStats
 * the active scenario installs via setActiveStats.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class InflightTracker {

    private val submitTimes = ConcurrentHashMap<Long, Long>()
    @Volatile private var active: LoadStats? = null

    fun setActiveStats(stats: LoadStats?) { this.active = stats }

    fun register(submissionId: Long, submitNs: Long) {
        submitTimes[submissionId] = submitNs
    }

    fun onTerminal(submissionId: Long) {
        val submitNs = submitTimes.remove(submissionId) ?: return
        val stats = active ?: return
        stats.recordE2E(submissionId, System.nanoTime() - submitNs)
    }

    fun inflightCount(): Int = submitTimes.size

    fun resetForScenario() {
        submitTimes.clear()
    }
}

/**
 * Listens for SubmissionTerminalEvent emitted by PipelineServiceImpl.handleResult
 * and forwards into the InflightTracker.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class TerminalEventListener(private val tracker: InflightTracker) {
    @EventListener
    fun on(event: SubmissionTerminalEvent) {
        tracker.onTerminal(event.submissionId)
    }
}
