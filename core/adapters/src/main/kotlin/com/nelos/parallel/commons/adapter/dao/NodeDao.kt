package com.nelos.parallel.commons.adapter.dao

import com.nelos.parallel.commons.adapter.entity.Node
import com.nelos.parallel.commons.dao.GenericDao

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface NodeDao : GenericDao<Node> {

    fun findByNodeId(nodeId: String): Node?

    fun deleteByIds(ids: Collection<Long>): Int
}
