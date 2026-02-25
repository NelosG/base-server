package com.nelos.parallel.commons.adapter

import com.fasterxml.jackson.databind.node.ObjectNode
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.request.*
import com.nelos.parallel.commons.adapter.vo.response.*

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
    fun cancelJob(node: NodeInfo, jobId: String): CancelJobResponse

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
    fun loadAdapter(node: NodeInfo, name: String, config: ObjectNode? = null): AdapterActionResult

    /**
     * Unloads an adapter from the specified node.
     */
    fun unloadAdapter(node: NodeInfo, name: String): AdapterActionResult

    /**
     * Updates dynamic configuration on the specified node.
     */
    fun updateConfig(node: NodeInfo, config: ConfigUpdateRequest): QueueStatus

    /**
     * Lists all loaded resource providers on the specified node.
     */
    fun listResourceProviders(node: NodeInfo): List<ResourceProviderInfo>

    /**
     * Lists all available (not yet loaded) resource providers on the specified node.
     */
    fun listAvailableResourceProviders(node: NodeInfo): List<ResourceProviderInfo>

    /**
     * Loads a resource provider on the specified node.
     */
    fun loadResourceProvider(node: NodeInfo, name: String, config: ObjectNode? = null): ResourceProviderActionResult

    /**
     * Unloads a resource provider from the specified node.
     */
    fun unloadResourceProvider(node: NodeInfo, name: String): ResourceProviderActionResult
}
