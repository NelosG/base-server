package com.nelos.parallel.gitlab.dao.impl

import com.nelos.parallel.commons.dao.impl.GenericDaoImpl
import com.nelos.parallel.gitlab.dao.SubmissionDao
import com.nelos.parallel.gitlab.entity.Submission
import com.nelos.parallel.gitlab.enums.SubmissionStatus
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Repository("prl.submissionDao")
class SubmissionDaoImpl : GenericDaoImpl<Submission>(), SubmissionDao {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByUserId(userId: Long): List<Submission> {
        return findByCondition { cb, _, root ->
            cb.equal(root.get<Long>("userId"), userId)
        }
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findPendingByMr(assignmentId: Long, mrIid: Long): Submission? {
        return findByCondition { cb, _, root ->
            cb.and(
                cb.equal(root.get<Long>("assignmentId"), assignmentId),
                cb.equal(root.get<Long>("mrIid"), mrIid),
                root.get<SubmissionStatus>("status").`in`(
                    SubmissionStatus.PENDING,
                    SubmissionStatus.DISPATCHED,
                ),
            )
        }.firstOrNull()
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun findPendingByMrForUpdate(assignmentId: Long, mrIid: Long): Submission? {
        val cb = entityManager.criteriaBuilder
        val cq = cb.createQuery(Submission::class.java)
        val root = cq.from(Submission::class.java)
        cq.where(
            cb.and(
                cb.equal(root.get<Long>("assignmentId"), assignmentId),
                cb.equal(root.get<Long>("mrIid"), mrIid),
                root.get<SubmissionStatus>("status").`in`(
                    SubmissionStatus.PENDING,
                    SubmissionStatus.DISPATCHED,
                ),
            )
        )
        return entityManager.createQuery(cq)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .firstOrNull()
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByJobId(jobId: Long): Submission? {
        return findByCondition { cb, _, root ->
            cb.equal(root.get<Long>("jobId"), jobId)
        }.firstOrNull()
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByStatusIn(statuses: Collection<SubmissionStatus>): List<Submission> {
        if (statuses.isEmpty()) return emptyList()
        return findByCondition { _, _, root ->
            root.get<SubmissionStatus>("status").`in`(statuses)
        }
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findFilteredPage(
        userId: Long?,
        assignmentId: Long?,
        offset: Int,
        limit: Int,
    ): List<Submission> {
        val cb = entityManager.criteriaBuilder
        val cq = cb.createQuery(Submission::class.java)
        val root = cq.from(Submission::class.java)
        val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()
        if (userId != null) predicates += cb.equal(root.get<Long>("userId"), userId)
        if (assignmentId != null) predicates += cb.equal(root.get<Long>("assignmentId"), assignmentId)
        if (predicates.isNotEmpty()) cq.where(*predicates.toTypedArray())
        cq.orderBy(cb.desc(root.get<Any>("createdAt")))
        return entityManager.createQuery(cq)
            .setFirstResult(offset.coerceAtLeast(0))
            .setMaxResults(limit.coerceAtLeast(1))
            .resultList
    }
}
