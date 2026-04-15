package com.nelos.parallel.gitlab.service

import com.nelos.parallel.commons.service.GenericService
import com.nelos.parallel.gitlab.entity.Submission
import com.nelos.parallel.gitlab.enums.SubmissionStatus

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface SubmissionService : GenericService<Submission> {

    fun findByUserId(userId: Long): List<Submission>

    fun findPendingByMr(assignmentId: Long, mrIid: Long): Submission?

    fun findPendingByMrForUpdate(assignmentId: Long, mrIid: Long): Submission?

    fun findByJobId(jobId: Long): Submission?

    fun findByStatusIn(statuses: Collection<SubmissionStatus>): List<Submission>

    fun findFilteredPage(
        userId: Long?,
        assignmentId: Long?,
        offset: Int,
        limit: Int,
    ): List<Submission>
}
