package com.nelos.parallel.gitlab.service

import com.nelos.parallel.commons.service.Service
import com.nelos.parallel.gitlab.entity.StudentGroupMember

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface StudentGroupMemberService : Service<StudentGroupMember> {

    fun findByGroupId(groupId: Long): List<StudentGroupMember>

    fun findByGroupAndUser(groupId: Long, userId: Long): StudentGroupMember?

    fun deleteByGroupId(groupId: Long)
}
