package com.nelos.parallel.adapters.rabbit

import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.pipeline.commons.service.RunnerSelector
import com.nelos.parallel.pipeline.runner.exception.RunnerInfraException
import com.nelos.parallel.pipeline.runner.service.RunHandle
import com.nelos.parallel.pipeline.runner.service.RunnerContext
import com.nelos.parallel.pipeline.runner.service.RunnerType
import com.nelos.parallel.pipeline.runner.service.TaskRunner
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Dispatches a [com.nelos.parallel.commons.adapter.vo.request.TaskSubmission]
 * to a remote engine node over AMQP. Result is delivered back through the
 * existing `RabbitResultListener` queue consumer - this runner does NOT
 * invoke [RunnerContext.onResult] itself.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.rabbitRunner")
class RabbitTaskRunner(
    @Qualifier("prl.rabbitNodeAdapter") private val rabbitAdapter: NodeAdapter,
    private val runnerSelector: RunnerSelector,
) : TaskRunner {

    override val type: RunnerType = RunnerType.AMQP
    override val name: String = "rabbit"

    override fun isAvailable(): Boolean = true

    override fun tryRun(ctx: RunnerContext): RunHandle? {
        val jobId = ctx.task.jobId
            ?: throw RunnerInfraException(name, "task has no jobId")
        val selected = runnerSelector.selectRunner(ctx.submissionId, TransportType.AMQP)
            ?: return null
        val node = selected.node
        LOG.info("dispatching submission {} via AMQP to node {}", ctx.submissionId, node.nodeId)
        try {
            rabbitAdapter.submitTask(node, ctx.task)
        } catch (rpcEx: Exception) {
            // Broker ack does not guarantee the engine has processed the task,
            // but conversely a failed ack does not mean the broker rejected
            // the message - it may have been delivered before the timeout
            // fired. Mirror the HTTP runner: log, keep the submission in
            // flight, rely on the eventual callback or stuck-cleanup.
            LOG.warn(
                "AMQP submitTask to node {} failed for submission {} - leaving in-flight: {}",
                node.nodeId, ctx.submissionId, rpcEx.message,
            )
        }
        return RabbitRunHandle(node.nodeId, jobId, rabbitAdapter, node)
    }

    private class RabbitRunHandle(
        val nodeId: String,
        override val jobId: String,
        private val rabbitAdapter: NodeAdapter,
        private val node: com.nelos.parallel.commons.adapter.vo.NodeInfo,
    ) : RunHandle {
        override val runnerName: String = "rabbit"

        override fun cancel() {
            try {
                rabbitAdapter.cancelJob(node, jobId)
            } catch (e: Exception) {
                LoggerFactory.getLogger(RabbitRunHandle::class.java)
                    .warn("AMQP cancelJob for {} on node {} failed: {}", jobId, nodeId, e.message)
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RabbitTaskRunner::class.java)
    }
}
