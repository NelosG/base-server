package com.nelos.parallel.pipeline.data.service.impl

import com.nelos.parallel.commons.service.impl.GenericServiceImpl
import com.nelos.parallel.pipeline.data.dao.SubmissionLogDao
import com.nelos.parallel.pipeline.data.entity.SubmissionLog
import com.nelos.parallel.pipeline.data.service.SubmissionLogService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.submissionLogService")
class SubmissionLogServiceImpl : GenericServiceImpl<SubmissionLog, SubmissionLogDao>(), SubmissionLogService {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findBySubmissionId(submissionId: Long): List<SubmissionLog> {
        return invokeDaoMethod { it.findBySubmissionId(submissionId) }
    }
}
