package com.nelos.parallel.gitlab.forms

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import com.nelos.parallel.gitlab.forms.vo.SubmissionDetailView
import com.nelos.parallel.gitlab.forms.vo.SubmissionListItemView
import com.nelos.parallel.pipeline.data.service.AssignmentService
import com.nelos.parallel.pipeline.data.service.SubmissionService
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Student view of their own submissions. Filters everything by the currently
 * authenticated user - students cannot see anyone else's data.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.mySubmissionsViewService", roles = [AppRole.STUDENT])
class MySubmissionsViewService(
    private val submissionService: SubmissionService,
    private val assignmentService: AssignmentService,
    private val userService: UserService,
    private val submissionViewService: SubmissionViewService,
) {

    fun getMySubmissions(): List<SubmissionListItemView> {
        val user = currentUser()
        val userId = user.id ?: error("Current user has no id")
        val users = mapOf<Long?, User>(userId to user)
        val assignments = assignmentService.findAll().associateBy { it.id }
        return submissionService.findByUserId(userId)
            .sortedByDescending { it.createdAt }
            .map { with(submissionViewService) { it.toListItem(users, assignments) } }
    }

    fun getMySubmission(id: Long): SubmissionDetailView {
        val userId = currentUser().id ?: error("Current user has no id")
        val submission = submissionService.tryFindById(id) ?: error("Submission $id not found")
        if (submission.userId != userId) error("Forbidden: submission $id does not belong to current user")
        return submissionViewService.buildDetail(submission)
    }

    private fun currentUser(): User {
        val auth = SecurityContextHolder.getContext().authentication
            ?: error("Not authenticated")
        return userService.findByLogin(auth.name) ?: error("Current user not found")
    }
}
