package com.nelos.parallel.commons.adapter.service.impl

import com.nelos.parallel.commons.adapter.dao.NodeDao
import com.nelos.parallel.commons.adapter.entity.Node
import com.nelos.parallel.commons.adapter.service.NodeService
import com.nelos.parallel.commons.service.impl.GenericServiceImpl
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.nodeService")
class NodeServiceImpl : GenericServiceImpl<Node, NodeDao>(), NodeService {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByNodeId(nodeId: String): Node? {
        return invokeDaoMethod { it.findByNodeId(nodeId) }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun deleteByIds(ids: Collection<Long>): Int {
        return invokeDaoMethod { it.deleteByIds(ids) }
    }
}
