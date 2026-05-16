package com.nelos.parallel.pipeline.data.service

import com.nelos.parallel.commons.service.CodeService
import com.nelos.parallel.pipeline.data.entity.Assignment

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface AssignmentService : CodeService<Assignment> {

    fun findByGitlabProjectPath(projectPath: String): Assignment?
}
