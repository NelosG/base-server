package com.nelos.parallel.pipeline.data.dao

import com.nelos.parallel.commons.dao.Dao
import com.nelos.parallel.pipeline.data.entity.StudentGroupMember

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface StudentGroupMemberDao : Dao<StudentGroupMember> {

    fun findByGroupId(groupId: Long): List<StudentGroupMember>

    fun findByGroupAndUser(groupId: Long, userId: Long): StudentGroupMember?

    fun deleteByGroupId(groupId: Long)
}
