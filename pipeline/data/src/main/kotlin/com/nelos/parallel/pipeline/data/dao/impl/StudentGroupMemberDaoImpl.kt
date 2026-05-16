package com.nelos.parallel.pipeline.data.dao.impl

import com.nelos.parallel.commons.dao.impl.DaoImpl
import com.nelos.parallel.pipeline.data.dao.StudentGroupMemberDao
import com.nelos.parallel.pipeline.data.entity.StudentGroupMember
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Repository("prl.studentGroupMemberDao")
class StudentGroupMemberDaoImpl : DaoImpl<StudentGroupMember>(), StudentGroupMemberDao {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByGroupId(groupId: Long): List<StudentGroupMember> {
        return findByCondition { cb, _, root ->
            cb.equal(root.get<Long>("groupId"), groupId)
        }
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByGroupAndUser(groupId: Long, userId: Long): StudentGroupMember? {
        return findByCondition { cb, _, root ->
            cb.and(
                cb.equal(root.get<Long>("groupId"), groupId),
                cb.equal(root.get<Long>("userId"), userId),
            )
        }.firstOrNull()
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun deleteByGroupId(groupId: Long) {
        val cb = entityManager.criteriaBuilder
        val cd = cb.createCriteriaDelete(entityClass)
        val root = cd.from(entityClass)
        cd.where(cb.equal(root.get<Long>("groupId"), groupId))
        entityManager.createQuery(cd).executeUpdate()
    }
}
