package com.nelos.parallel.gitlab.forms

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import com.nelos.parallel.commons.view.vo.PageRequest
import com.nelos.parallel.commons.view.vo.PagedView
import com.nelos.parallel.gitlab.forms.vo.SubmissionDetailView
import com.nelos.parallel.gitlab.forms.vo.SubmissionListItemView
import com.nelos.parallel.gitlab.service.GitlabUserService
import com.nelos.parallel.pipeline.data.entity.Assignment
import com.nelos.parallel.pipeline.data.entity.Submission
import com.nelos.parallel.pipeline.data.service.AssignmentService
import com.nelos.parallel.pipeline.data.service.SubmissionResultService
import com.nelos.parallel.pipeline.data.service.SubmissionService
import org.springframework.beans.factory.annotation.Value

/**
 * Admin view of submissions: lists everyone's submissions, shows detailed info including
 * persisted log and result JSON.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.submissionViewService", roles = [AppRole.ADMIN])
class SubmissionViewService(
    private val submissionService: SubmissionService,
    private val submissionResultService: SubmissionResultService,
    private val assignmentService: AssignmentService,
    private val userService: UserService,
    private val gitlabUserService: GitlabUserService,
    @param:Value("\${gitlab.url:}") private val gitlabUrl: String,
) {

    fun getSubmissions(
        userId: Long?,
        assignmentId: Long?,
        page: PageRequest?,
    ): PagedView<SubmissionListItemView> {
        // Fetch one extra row as a hasMore probe - avoids a separate COUNT(*).
        val offset = page?.offset ?: 0
        val limit = (page?.limit ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        val rows = submissionService.findFilteredPage(userId, assignmentId, offset, limit + 1)
        val hasMore = rows.size > limit
        val pageRows = if (hasMore) rows.subList(0, limit) else rows
        val users = userService.findAll().associateBy { it.id }
        val assignments = assignmentService.findAll().associateBy { it.id }
        return PagedView(
            items = pageRows.map { it.toListItem(users, assignments) },
            hasMore = hasMore,
        )
    }

    fun getSubmission(id: Long): SubmissionDetailView {
        val submission = submissionService.tryFindById(id) ?: error("Submission $id not found")
        return buildDetail(submission)
    }

    internal fun buildDetail(submission: Submission): SubmissionDetailView {
        val id = submission.id ?: error("Submission has no id")
        val user = submission.userId?.let { userService.tryFindById(it) }
        val assignment = submission.assignmentId?.let { assignmentService.tryFindById(it) }
        val gitlabName = submission.userId?.let { gitlabUserService.findByUserId(it)?.gitLabName }
        val result = submissionResultService.findBySubmissionId(id)
        return SubmissionDetailView(
            id = id,
            assignmentId = assignment?.id,
            assignmentCode = assignment?.code,
            assignmentName = assignment?.name,
            userId = user?.id,
            login = user?.login,
            displayName = user?.displayName,
            gitlabName = gitlabName,
            status = submission.status?.name,
            mrIid = submission.mrIid,
            mrUrl = mrUrl(assignment, submission.mrIid),
            sourceBranch = submission.sourceBranch,
            solutionRepoUrl = submission.solutionRepoUrl,
            commitSha = submission.commitSha,
            createdAt = submission.createdAt?.toString(),
            completedAt = submission.completedAt?.toString(),
            resultSummary = submission.resultSummary,
            logText = result?.logText,
            resultJson = result?.resultJson,
        )
    }

    internal fun Submission.toListItem(
        users: Map<Long?, User>,
        assignments: Map<Long?, Assignment>,
    ): SubmissionListItemView {
        val user = users[userId]
        val assignment = assignments[assignmentId]
        return SubmissionListItemView(
            id = id,
            assignmentId = assignment?.id,
            assignmentCode = assignment?.code,
            assignmentName = assignment?.name,
            userId = user?.id,
            login = user?.login,
            displayName = user?.displayName,
            status = status?.name,
            mrIid = mrIid,
            mrUrl = mrUrl(assignment, mrIid),
            commitSha = commitSha,
            createdAt = createdAt?.toString(),
            completedAt = completedAt?.toString(),
            resultSummary = resultSummary,
        )
    }

    private fun mrUrl(assignment: Assignment?, mrIid: Long?): String? {
        val path = assignment?.gitlabProjectPath ?: return null
        val iid = mrIid ?: return null
        val base = gitlabUrl.trimEnd('/').ifBlank { return null }
        return "$base/$path/-/merge_requests/$iid"
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 50
        private const val MAX_PAGE_SIZE = 200
    }
}
