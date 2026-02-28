package com.nelos.parallel.commons.adapter.listener

import com.nelos.parallel.commons.adapter.vo.response.TaskResult

/**
 * Callback interface for receiving task results from test-runner nodes.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
fun interface TaskResultListener {

    /**
     * Called when a task result is received from a node.
     */
    fun onTaskResult(result: TaskResult)
}