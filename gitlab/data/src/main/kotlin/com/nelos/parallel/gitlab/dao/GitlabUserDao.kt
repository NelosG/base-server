package com.nelos.parallel.gitlab.dao

import com.nelos.parallel.commons.dao.Dao
import com.nelos.parallel.gitlab.entity.GitlabUser

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface GitlabUserDao : Dao<GitlabUser> {

    fun findByGitlabName(gitlabName: String): GitlabUser?

    fun findByUserId(userId: Long): GitlabUser?
}
