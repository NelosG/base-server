package com.nelos.parallel.load

import com.nelos.parallel.commons.adapter.vo.response.ResultSummary
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.commons.adapter.vo.response.TestSummary
import com.nelos.parallel.pipeline.runner.service.RunHandle
import com.nelos.parallel.pipeline.runner.service.RunnerContext
import com.nelos.parallel.pipeline.runner.service.RunnerType
import com.nelos.parallel.pipeline.runner.service.TaskRunner
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Stub engine for load tests. Assumes infinite engine compute: each submission
 * is scheduled into a timer wheel to fire after taskDurationMs regardless of
 * how many others are in flight. callbackPoolSize caps only the concurrent
 * execution of the callback, not engine work.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class LoadTestTaskRunner(
    private val taskDurationMs: Long,
    callbackPoolSize: Int,
) : TaskRunner {

    override val type: RunnerType = RunnerType.LOCAL
    override val name: String = "loadtest"

    private val executor: ScheduledExecutorService =
        Executors.newScheduledThreadPool(callbackPoolSize.coerceAtLeast(1)) { r ->
            Thread(r, "loadtest-engine").apply { isDaemon = true }
        }

    override fun isAvailable(): Boolean = !executor.isShutdown

    override fun tryRun(ctx: RunnerContext): RunHandle? {
        val jobId = ctx.task.jobId ?: ctx.submissionId.toString()
        executor.schedule({
            try {
                ctx.onResult(buildResult(jobId))
            } catch (e: Exception) {
                LOG.error("loadtest callback failed for submission {}", ctx.submissionId, e)
            }
        }, taskDurationMs, TimeUnit.MILLISECONDS)
        return Handle(jobId)
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun buildResult(jobId: String): TaskResult = TaskResult(
        jobId = jobId,
        nodeId = "loadtest",
        status = "completed",
        durationMs = taskDurationMs.toDouble(),
        summary = ResultSummary(
            correctness = TestSummary(totalTests = 10, passed = 10, failed = 0),
        ),
    )

    private class Handle(override val jobId: String) : RunHandle {
        override val runnerName: String = "loadtest"
        override fun cancel() { /* no-op for stub */ }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(LoadTestTaskRunner::class.java)
    }
}
