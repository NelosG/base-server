package com.nelos.parallel.commons.adapter.dao.impl

import com.nelos.parallel.commons.adapter.dao.NodeDao
import com.nelos.parallel.commons.adapter.entity.Node
import com.nelos.parallel.commons.dao.impl.GenericDaoImpl
import com.nelos.parallel.commons.entity.AbstractEntity
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Repository("prl.nodeDao")
class NodeDaoImpl : GenericDaoImpl<Node>(), NodeDao {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByNodeId(nodeId: String): Node? {
        return findByCondition { cb, _, root ->
            cb.equal(root.get<String>("nodeId"), nodeId)
        }.firstOrNull()
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun findByNodeIdForUpdate(nodeId: String): Node? =
        findByConditionForUpdate { cb, _, root ->
            cb.equal(root.get<String>("nodeId"), nodeId)
        }.firstOrNull()

    @Transactional(propagation = Propagation.REQUIRED)
    override fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        val cb = entityManager.criteriaBuilder
        val cd = cb.createCriteriaDelete(entityClass)
        val root = cd.from(entityClass)
        cd.where(root.get<Long>(AbstractEntity.ID).`in`(ids))
        return entityManager.createQuery(cd).executeUpdate()
    }
}
