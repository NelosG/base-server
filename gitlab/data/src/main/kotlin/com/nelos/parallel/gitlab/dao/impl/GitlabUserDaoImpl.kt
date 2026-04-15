package com.nelos.parallel.gitlab.dao.impl

import com.nelos.parallel.commons.dao.impl.DaoImpl
import com.nelos.parallel.gitlab.dao.GitlabUserDao
import com.nelos.parallel.gitlab.entity.GitlabUser
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Repository("prl.gitlabUserDao")
class GitlabUserDaoImpl : DaoImpl<GitlabUser>(), GitlabUserDao {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByGitlabName(gitlabName: String): GitlabUser? {
        return findByCondition { cb, _, root ->
            cb.equal(root.get<String>("gitLabName"), gitlabName)
        }.firstOrNull()
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByUserId(userId: Long): GitlabUser? {
        return findByCondition { cb, _, root ->
            cb.equal(root.get<Long>("userId"), userId)
        }.firstOrNull()
    }
}
