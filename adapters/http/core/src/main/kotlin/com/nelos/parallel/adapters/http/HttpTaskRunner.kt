package com.nelos.parallel.adapters.http

import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.pipeline.commons.service.RunnerSelector
import com.nelos.parallel.pipeline.runner.exception.RunnerInfraException
import com.nelos.parallel.pipeline.runner.service.RunHandle
import com.nelos.parallel.pipeline.runner.service.RunnerContext
import com.nelos.parallel.pipeline.runner.service.RunnerType
import com.nelos.parallel.pipeline.runner.service.TaskRunner
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Dispatches a [com.nelos.parallel.commons.adapter.vo.request.TaskSubmission]
 * to a remote engine node over HTTP. Result is delivered back through the
 * existing `HttpTaskResultCallbackController` callback path - this runner
 * does NOT invoke [RunnerContext.onResult] itself.
 *
 * Failover semantics:
 *  - no healthy HTTP node       -> `null` (manager falls over)
 *  - submitTask RPC failure     -> log + return a handle anyway (the engine
 *                                  may still have received the task, so we
 *                                  leave dispatch state in place and rely on
 *                                  `StuckSubmissionCleanupJob` for the genuine
 *                                  "engine never came back" case). This
 *                                  matches the original `PipelineServiceImpl`
 *                                  behavior pre-runner-refactor.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.httpRunner")
class HttpTaskRunner(
    private val httpAdapter: HttpNodeAdapter,
    private val runnerSelector: RunnerSelector,
) : TaskRunner {

    override val type: RunnerType = RunnerType.HTTP
    override val name: String = "http"

    // Cheap snapshot probe: do we have any node carrying HTTP transport at all?
    // The authoritative health-check happens inside selectRunner below.
    override fun isAvailable(): Boolean = true

    override fun tryRun(ctx: RunnerContext): RunHandle? {
        val jobId = ctx.task.jobId
            ?: throw RunnerInfraException(name, "task has no jobId")
        val selected = runnerSelector.selectRunner(ctx.submissionId, TransportType.HTTP)
            ?: return null
        val node = selected.node
        LOG.info("dispatching submission {} via HTTP to node {}", ctx.submissionId, node.nodeId)
        try {
            httpAdapter.submitTask(node, ctx.task)
        } catch (rpcEx: Exception) {
            // The RPC ack is not the result. A timed-out / failed ack might still
            // mean the engine received the job - in which case its later HTTP
            // callback to /api/callback/result will land. Don't fail the
            // submission here; surface the warning and rely on stuck-cleanup.
            LOG.warn(
                "HTTP submitTask to node {} failed for submission {} - leaving in-flight: {}",
                node.nodeId, ctx.submissionId, rpcEx.message,
            )
        }
        return HttpRunHandle(node.nodeId, jobId, httpAdapter, node)
    }

    private class HttpRunHandle(
        val nodeId: String,
        override val jobId: String,
        private val httpAdapter: HttpNodeAdapter,
        private val node: com.nelos.parallel.commons.adapter.vo.NodeInfo,
    ) : RunHandle {
        override val runnerName: String = "http"

        override fun cancel() {
            try {
                httpAdapter.cancelJob(node, jobId)
            } catch (e: Exception) {
                LoggerFactory.getLogger(HttpRunHandle::class.java)
                    .warn("HTTP cancelJob for {} on node {} failed: {}", jobId, nodeId, e.message)
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(HttpTaskRunner::class.java)
    }
}
