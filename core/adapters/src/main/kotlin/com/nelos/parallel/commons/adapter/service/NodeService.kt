package com.nelos.parallel.commons.adapter.service

import com.nelos.parallel.commons.adapter.entity.Node
import com.nelos.parallel.commons.service.GenericService

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface NodeService : GenericService<Node> {

    fun findByNodeId(nodeId: String): Node?

    /**
     * Same as [findByNodeId] but acquires a `SELECT ... FOR UPDATE` row lock.
     * Use in register / removeTransport / updateNode flows where concurrent
     * writers on the same node must serialize. Must run inside a writable
     * transaction.
     */
    fun findByNodeIdForUpdate(nodeId: String): Node?

    fun deleteByIds(ids: Collection<Long>): Int
}
