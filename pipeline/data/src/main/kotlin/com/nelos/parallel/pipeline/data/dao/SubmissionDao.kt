package com.nelos.parallel.pipeline.data.dao

import com.nelos.parallel.commons.dao.GenericDao
import com.nelos.parallel.pipeline.data.entity.Submission

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface SubmissionDao : GenericDao<Submission> {

    fun findByUserId(userId: Long): List<Submission>

    fun findPendingByMr(assignmentId: Long, mrIid: Long): Submission?

    /**
     * Same as [findPendingByMr] but acquires a `PESSIMISTIC_WRITE` row lock so concurrent
     * submitters for the same MR serialize on the existing pending row.
     */
    fun findPendingByMrForUpdate(assignmentId: Long, mrIid: Long): Submission?

    fun findByJobId(jobId: Long): Submission?

    fun findByStatusIn(statuses: Collection<com.nelos.parallel.pipeline.commons.enums.SubmissionStatus>): List<Submission>

    /**
     * Paged + filtered list ordered by `createdAt` DESC (newest first).
     * `userId` and/or `assignmentId` are optional; null means "any".
     */
    fun findFilteredPage(
        userId: Long?,
        assignmentId: Long?,
        offset: Int,
        limit: Int,
    ): List<Submission>
}
