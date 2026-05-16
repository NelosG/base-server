package com.nelos.parallel.pipeline.data.service.impl

import com.nelos.parallel.commons.service.impl.CodeServiceImpl
import com.nelos.parallel.pipeline.data.dao.AssignmentDao
import com.nelos.parallel.pipeline.data.entity.Assignment
import com.nelos.parallel.pipeline.data.service.AssignmentService
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
