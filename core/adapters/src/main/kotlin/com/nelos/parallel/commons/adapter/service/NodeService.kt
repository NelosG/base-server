package com.nelos.parallel.commons.adapter.service

import com.nelos.parallel.commons.adapter.entity.Node
import com.nelos.parallel.commons.service.GenericService

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface NodeService : GenericService<Node> {

    fun findByNodeId(nodeId: String): Node?

    fun deleteByIds(ids: Collection<Long>): Int
}
