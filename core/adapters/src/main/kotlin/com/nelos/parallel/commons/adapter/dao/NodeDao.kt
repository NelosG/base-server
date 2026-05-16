package com.nelos.parallel.commons.adapter.dao

import com.nelos.parallel.commons.adapter.entity.Node
import com.nelos.parallel.commons.dao.GenericDao

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface NodeDao : GenericDao<Node> {

    fun findByNodeId(nodeId: String): Node?

    /**
     * Same as [findByNodeId] but acquires a `SELECT ... FOR UPDATE` row lock,
     * blocking other writers on the same node until the surrounding transaction
     * commits. Must be called inside a writable transaction.
     */
    fun findByNodeIdForUpdate(nodeId: String): Node?

    fun deleteByIds(ids: Collection<Long>): Int
}
