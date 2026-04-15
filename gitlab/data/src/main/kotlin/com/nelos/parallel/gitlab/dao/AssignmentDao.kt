package com.nelos.parallel.gitlab.dao

import com.nelos.parallel.commons.dao.CodeDao
import com.nelos.parallel.gitlab.entity.Assignment

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface AssignmentDao : CodeDao<Assignment> {

    fun findByGitlabProjectPath(projectPath: String): Assignment?
}
