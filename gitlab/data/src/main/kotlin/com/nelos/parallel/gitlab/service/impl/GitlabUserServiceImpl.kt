package com.nelos.parallel.gitlab.service.impl

import com.nelos.parallel.commons.service.impl.ServiceImpl
import com.nelos.parallel.gitlab.dao.GitlabUserDao
import com.nelos.parallel.gitlab.entity.GitlabUser
import com.nelos.parallel.gitlab.service.GitlabUserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.gitlabUserService")
class GitlabUserServiceImpl : ServiceImpl<GitlabUser, GitlabUserDao>(), GitlabUserService {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByGitlabName(gitlabName: String): GitlabUser? {
        return invokeDaoMethod { it.findByGitlabName(gitlabName) }
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByUserId(userId: Long): GitlabUser? {
        return invokeDaoMethod { it.findByUserId(userId) }
    }
}
