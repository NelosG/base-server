package com.nelos.parallel.gitlab.service

import com.nelos.parallel.commons.service.Service
import com.nelos.parallel.gitlab.entity.GitlabUser

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface GitlabUserService : Service<GitlabUser> {

    fun findByGitlabName(gitlabName: String): GitlabUser?

    fun findByUserId(userId: Long): GitlabUser?
}
