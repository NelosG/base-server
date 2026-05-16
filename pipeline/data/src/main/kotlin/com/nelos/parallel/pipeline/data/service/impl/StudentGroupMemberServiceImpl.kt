package com.nelos.parallel.pipeline.data.service.impl

import com.nelos.parallel.commons.service.impl.ServiceImpl
import com.nelos.parallel.pipeline.data.dao.StudentGroupMemberDao
import com.nelos.parallel.pipeline.data.entity.StudentGroupMember
import com.nelos.parallel.pipeline.data.service.StudentGroupMemberService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.studentGroupMemberService")
class StudentGroupMemberServiceImpl : ServiceImpl<StudentGroupMember, StudentGroupMemberDao>(),
    StudentGroupMemberService {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByGroupId(groupId: Long): List<StudentGroupMember> {
        return invokeDaoMethod { it.findByGroupId(groupId) }
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByGroupAndUser(groupId: Long, userId: Long): StudentGroupMember? {
        return invokeDaoMethod { it.findByGroupAndUser(groupId, userId) }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun deleteByGroupId(groupId: Long) {
        invokeDaoMethod { it.deleteByGroupId(groupId) }
    }
}
