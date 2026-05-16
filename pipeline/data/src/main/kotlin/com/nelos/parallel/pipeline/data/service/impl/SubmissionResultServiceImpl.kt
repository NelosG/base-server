package com.nelos.parallel.pipeline.data.service.impl

import com.nelos.parallel.commons.service.impl.GenericServiceImpl
import com.nelos.parallel.pipeline.data.dao.SubmissionResultDao
import com.nelos.parallel.pipeline.data.entity.SubmissionResult
import com.nelos.parallel.pipeline.data.service.SubmissionResultService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.submissionResultService")
class SubmissionResultServiceImpl : GenericServiceImpl<SubmissionResult, SubmissionResultDao>(),
    SubmissionResultService {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findBySubmissionId(submissionId: Long): SubmissionResult? {
        return invokeDaoMethod { it.findBySubmissionId(submissionId) }
    }
}
