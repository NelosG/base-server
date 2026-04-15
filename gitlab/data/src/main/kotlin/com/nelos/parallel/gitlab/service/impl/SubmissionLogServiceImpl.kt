package com.nelos.parallel.gitlab.service.impl

import com.nelos.parallel.commons.service.impl.GenericServiceImpl
import com.nelos.parallel.gitlab.dao.SubmissionLogDao
import com.nelos.parallel.gitlab.entity.SubmissionLog
import com.nelos.parallel.gitlab.service.SubmissionLogService
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
