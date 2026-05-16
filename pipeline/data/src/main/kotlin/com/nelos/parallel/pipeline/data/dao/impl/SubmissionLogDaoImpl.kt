package com.nelos.parallel.pipeline.data.dao.impl

import com.nelos.parallel.commons.dao.impl.GenericDaoImpl
import com.nelos.parallel.commons.entity.AbstractEntity
import com.nelos.parallel.pipeline.data.dao.SubmissionLogDao
import com.nelos.parallel.pipeline.data.entity.SubmissionLog
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Repository("prl.submissionLogDao")
class SubmissionLogDaoImpl : GenericDaoImpl<SubmissionLog>(), SubmissionLogDao {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findBySubmissionId(submissionId: Long): List<SubmissionLog> {
        val cb = entityManager.criteriaBuilder
        val cq = cb.createQuery(SubmissionLog::class.java)
        val root = cq.from(SubmissionLog::class.java)
        cq.where(cb.equal(root.get<Long>("submissionId"), submissionId))
        cq.orderBy(cb.asc(root.get<Long>(AbstractEntity.ID)))
        return entityManager.createQuery(cq).resultList
    }
}
