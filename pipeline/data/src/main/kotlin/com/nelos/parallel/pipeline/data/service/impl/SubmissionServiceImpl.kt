package com.nelos.parallel.pipeline.data.service.impl

import com.nelos.parallel.commons.service.impl.GenericServiceImpl
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.data.dao.SubmissionDao
import com.nelos.parallel.pipeline.data.entity.Submission
import com.nelos.parallel.pipeline.data.service.SubmissionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.submissionService")
class SubmissionServiceImpl : GenericServiceImpl<Submission, SubmissionDao>(), SubmissionService {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByUserId(userId: Long): List<Submission> {
        return invokeDaoMethod { it.findByUserId(userId) }
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findPendingByMr(assignmentId: Long, mrIid: Long): Submission? {
        return invokeDaoMethod { it.findPendingByMr(assignmentId, mrIid) }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun findPendingByMrForUpdate(assignmentId: Long, mrIid: Long): Submission? {
        return invokeDaoMethod { it.findPendingByMrForUpdate(assignmentId, mrIid) }
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByJobId(jobId: Long): Submission? {
        return invokeDaoMethod { it.findByJobId(jobId) }
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByStatusIn(statuses: Collection<SubmissionStatus>): List<Submission> {
        return invokeDaoMethod { it.findByStatusIn(statuses) }
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findFilteredPage(
        userId: Long?,
        assignmentId: Long?,
        offset: Int,
        limit: Int,
    ): List<Submission> {
        return invokeDaoMethod { it.findFilteredPage(userId, assignmentId, offset, limit) }
    }
}
