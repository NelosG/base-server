package com.nelos.parallel.pipeline.data.dao

import com.nelos.parallel.commons.dao.CodeDao
import com.nelos.parallel.pipeline.data.entity.Assignment

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface AssignmentDao : CodeDao<Assignment> {

    fun findByGitlabProjectPath(projectPath: String): Assignment?
}
