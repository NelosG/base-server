package com.nelos.parallel.gitlab.dao.impl

import com.nelos.parallel.commons.dao.impl.CodeDaoImpl
import com.nelos.parallel.gitlab.dao.AssignmentDao
import com.nelos.parallel.gitlab.entity.Assignment
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Repository("prl.assignmentDao")
class AssignmentDaoImpl : CodeDaoImpl<Assignment>(), AssignmentDao {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByGitlabProjectPath(projectPath: String): Assignment? {
        return findByCondition { cb, _, root ->
            cb.equal(root.get<String>("gitlabProjectPath"), projectPath)
        }.firstOrNull()
    }
}
