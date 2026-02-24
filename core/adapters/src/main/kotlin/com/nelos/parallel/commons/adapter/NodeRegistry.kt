package com.nelos.parallel.commons.adapter

import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo

/**
 * Registry for managing known test-runner nodes.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface NodeRegistry {

    /**
     * Registers a node and returns its stored info.
     */
    fun register(node: NodeInfo): NodeInfo

    /**
     * Deregisters a node by its [nodeId].
     */
    fun deregister(nodeId: String): Boolean

    /**
     * Finds a node by its [nodeId], or `null` if not found.
     */
    fun findById(nodeId: String): NodeInfo?

    /**
     * Returns all registered nodes.
     */
    fun findAll(): List<NodeInfo>

    /**
     * Returns all nodes using the specified [transport].
     */
    fun findByTransport(transport: TransportType): List<NodeInfo>
}
