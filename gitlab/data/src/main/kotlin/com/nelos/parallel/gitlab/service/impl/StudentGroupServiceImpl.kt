package com.nelos.parallel.gitlab.service.impl

import com.nelos.parallel.commons.service.impl.GenericServiceImpl
import com.nelos.parallel.gitlab.dao.StudentGroupDao
import com.nelos.parallel.gitlab.entity.StudentGroup
import com.nelos.parallel.gitlab.service.StudentGroupService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.studentGroupService")
class StudentGroupServiceImpl : GenericServiceImpl<StudentGroup, StudentGroupDao>(), StudentGroupService {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByName(name: String): StudentGroup? {
        return invokeDaoMethod { it.findByName(name) }
    }
}
