package com.nelos.parallel.gitlab.service.impl

import com.nelos.parallel.commons.service.impl.CodeServiceImpl
import com.nelos.parallel.gitlab.dao.AssignmentDao
import com.nelos.parallel.gitlab.entity.Assignment
import com.nelos.parallel.gitlab.service.AssignmentService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.assignmentService")
class AssignmentServiceImpl : CodeServiceImpl<Assignment, AssignmentDao>(), AssignmentService {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByGitlabProjectPath(projectPath: String): Assignment? {
        return invokeDaoMethod { it.findByGitlabProjectPath(projectPath) }
    }
}
