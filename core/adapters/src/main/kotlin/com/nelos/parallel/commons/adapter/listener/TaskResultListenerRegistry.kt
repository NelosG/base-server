package com.nelos.parallel.commons.adapter.listener

import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry that dispatches [TaskResult]s to listeners registered for specific job IDs.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.taskResultListenerRegistry")
class TaskResultListenerRegistry {

    private val listeners = ConcurrentHashMap<String, TaskResultListener>()

    fun register(jobId: String, listener: TaskResultListener) {
        listeners[jobId] = listener
    }

    fun unregister(jobId: String) {
        listeners.remove(jobId)
    }

    fun dispatch(result: TaskResult) {
        val jobId = result.jobId ?: return
        val listener = listeners.remove(jobId) ?: return
        runCatching { listener.onTaskResult(result) }
            .onFailure { LOG.error("Error in task result listener for job {}: {}", jobId, it.message, it) }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(TaskResultListenerRegistry::class.java)
    }
}
