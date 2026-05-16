package com.nelos.parallel.pipeline.data.dao.impl

import com.nelos.parallel.commons.dao.impl.GenericDaoImpl
import com.nelos.parallel.pipeline.data.dao.SubmissionResultDao
import com.nelos.parallel.pipeline.data.entity.SubmissionResult
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Repository("prl.submissionResultDao")
class SubmissionResultDaoImpl : GenericDaoImpl<SubmissionResult>(), SubmissionResultDao {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findBySubmissionId(submissionId: Long): SubmissionResult? {
        return findByCondition { cb, _, root ->
            cb.equal(root.get<Long>("submissionId"), submissionId)
        }.firstOrNull()
    }
}
