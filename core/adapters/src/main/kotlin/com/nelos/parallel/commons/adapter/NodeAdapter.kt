package com.nelos.parallel.commons.adapter

import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.*

/**
 * Adapter interface for communicating with test-runner nodes.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface NodeAdapter {

    /**
     * The transport type this adapter uses.
     */
    val transportType: TransportType

    /**
     * Submits a task to the specified node.
     */
    fun submitTask(node: NodeInfo, task: TaskSubmission): TaskSubmissionResponse

    /**
     * Queries the status of a job on the specified node.
     */
    fun queryJobStatus(node: NodeInfo, jobId: String): TaskResult

    /**
     * Cancels a running job on the specified node.
     */
    fun cancelJob(node: NodeInfo, jobId: String): Boolean

    /**
     * Queries the runtime status of the specified node.
     */
    fun queryNodeStatus(node: NodeInfo): NodeStatus

    /**
     * Performs a health check on the specified node.
     */
    fun healthCheck(node: NodeInfo): Boolean

    /**
     * Queries the queue status overview of the specified node.
     */
    fun queueStatus(node: NodeInfo): QueueStatus

    /**
     * Lists all loaded adapters on the specified node.
     */
    fun listAdapters(node: NodeInfo): List<AdapterInfo>

    /**
     * Lists all available (downloadable) adapters on the specified node.
     */
    fun listAvailableAdapters(node: NodeInfo): List<AdapterInfo>

    /**
     * Loads an adapter on the specified node.
     */
    fun loadAdapter(node: NodeInfo, name: String, config: Map<String, Any?>? = null): AdapterActionResult

    /**
     * Unloads an adapter from the specified node.
     */
    fun unloadAdapter(node: NodeInfo, name: String): AdapterActionResult
}
