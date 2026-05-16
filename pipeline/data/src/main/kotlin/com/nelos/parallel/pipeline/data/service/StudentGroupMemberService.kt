package com.nelos.parallel.pipeline.data.service

import com.nelos.parallel.commons.service.Service
import com.nelos.parallel.pipeline.data.entity.StudentGroupMember

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface StudentGroupMemberService : Service<StudentGroupMember> {

    fun findByGroupId(groupId: Long): List<StudentGroupMember>

    fun findByGroupAndUser(groupId: Long, userId: Long): StudentGroupMember?

    fun deleteByGroupId(groupId: Long)
}
