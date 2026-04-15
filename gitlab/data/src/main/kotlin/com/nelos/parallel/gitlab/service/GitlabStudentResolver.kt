package com.nelos.parallel.gitlab.service

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.gitlab.entity.GitlabUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Resolves a GitLab username to an orchestrator user.
 * Three-step lookup used by pipeline submit and admin UI:
 *   1. Existing [GitlabUser] mapping => reuse the linked [User].
 *   2. Existing user with `login == gitlabName` => attach a new [GitlabUser] mapping.
 *   3. Otherwise => create a new student with a one-time random password and a [GitlabUser] mapping.
 *
 * Each path runs inside a single transaction so partial state (user without mapping,
 * or mapping without user) is impossible. Concurrent first-time MRs from the same GitLab
 * user are guarded by the `uk_prl_gitlab_user_gitlab_name` unique constraint - the loser
 * gets a `DataIntegrityViolationException` that rolls back its transaction; the CI then
 * retries and the second attempt finds the winner's mapping in step 1.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.gitlabStudentResolver")
class GitlabStudentResolver(
    private val userService: UserService,
    private val gitlabUserService: GitlabUserService,
    private val userDetailsService: UserDetailsProviderService,
) {

    @Transactional(propagation = Propagation.REQUIRED)
    fun resolveOrAutoCreate(gitlabName: String): User =
        existingUser(gitlabName) ?: createMapping(gitlabName)

    private fun existingUser(gitlabName: String): User? {
        gitlabUserService.findByGitlabName(gitlabName)?.let { link ->
            val userId = link.userId ?: error("GitlabUser '${link.gitLabName}' has no userId")
            return userService.tryFindById(userId)
                ?: error("User $userId referenced by GitlabUser '${link.gitLabName}' not found")
        }
        userService.findByLogin(gitlabName)?.let { existing ->
            attachMapping(existing, gitlabName)
            LOG.info("Attached gitlab mapping '{}' to existing user id={}", gitlabName, existing.id)
            return existing
        }
        return null
    }

    private fun createMapping(gitlabName: String): User {
        val (user, _) = userDetailsService.createUserWithRandomPassword(
            login = gitlabName,
            displayName = gitlabName,
            type = UserType.STUDENT,
        )
        attachMapping(user, gitlabName)
        LOG.info("Auto-created STUDENT user id={} for gitlab name '{}'", user.id, gitlabName)
        return user
    }

    private fun attachMapping(user: User, gitlabName: String) {
        gitlabUserService.save(GitlabUser().apply {
            this.userId = user.id ?: error("user has no id")
            this.gitLabName = gitlabName
        })
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GitlabStudentResolver::class.java)
    }
}
