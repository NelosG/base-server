package com.nelos.parallel.gitlab.service

import com.nelos.parallel.commons.service.CodeService
import com.nelos.parallel.gitlab.entity.Assignment

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface AssignmentService : CodeService<Assignment> {

    fun findByGitlabProjectPath(projectPath: String): Assignment?
}
