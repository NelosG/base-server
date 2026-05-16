package com.nelos.parallel.pipeline.core.service.impl

import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.jobs.service.JobService
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.data.service.SubmissionService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.LocalDateTime

/**
 * Transitions submissions stuck in PENDING/DISPATCHED beyond
 * `parallel.submission.stuck-timeout` into TIMEOUT.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.stuckSubmissionCleanupJob")
class StuckSubmissionCleanupJob(
    private val submissionService: SubmissionService,
    private val jobService: JobService,
    private val submissionLogger: SubmissionLogger,
    @Value("\${parallel.submission.stuck-timeout:600000}") private val stuckTimeout: Duration,
    transactionManager: PlatformTransactionManager,
) {

    private val txTemplate = TransactionTemplate(transactionManager)

    @Scheduled(fixedDelayString = "PT1M")
    fun cleanup() {
        val cutoff = LocalDateTime.now().minus(stuckTimeout)
        val candidates = submissionService.findByStatusIn(STUCK_STATUSES).filter {
            (it.createdAt ?: LocalDateTime.MAX).isBefore(cutoff)
        }
        if (candidates.isEmpty()) return
        LOG.warn("Checking {} candidate stuck submission(s) (older than {})", candidates.size, stuckTimeout)
        val timeoutMin = stuckTimeout.toMinutes()
        // Per-submission tx so one DB hiccup on one row doesn't abort the whole
        // batch - the next scheduled tick re-finds the still-stuck submissions.
        // Inside each tx we reload the row WITH A LOCK and re-check the status:
        // an engine callback for the same submission might have raced this tick
        // and already marked it COMPLETED/FAILED, in which case we must NOT
        // overwrite that with TIMEOUT.
        candidates.forEach { candidate ->
            val submissionId = candidate.id ?: return@forEach
            runCatching {
                txTemplate.executeWithoutResult {
                    val submission = submissionService.tryFindByIdForUpdate(submissionId) ?: return@executeWithoutResult
                    if (submission.status !in STUCK_STATUSES) {
                        // A concurrent handleResult finalized this submission while we
                        // were waiting for the lock; nothing to do.
                        return@executeWithoutResult
                    }
                    submission.status = SubmissionStatus.TIMEOUT
                    submission.completedAt = LocalDateTime.now()
                    submission.resultSummary = "Engine timeout - no result received within $timeoutMin min"
                    submissionService.save(submission)
                    submission.jobId?.let { jobId ->
                        jobService.tryFindByIdForUpdate(jobId)?.takeIf { it.status in OPEN_JOB_STATUSES }?.let { job ->
                            job.status = JobStatus.ERROR
                            job.endDate = LocalDateTime.now()
                            jobService.save(job)
                        }
                    }
                    submissionLogger.appendOne(
                        submissionId,
                        "[parallel] [TIMEOUT] Submission timed out after $timeoutMin min waiting for runner"
                    )
                }
            }.onFailure {
                LOG.warn("Failed to fail-stuck submission {}: {} (will retry on next tick)", submissionId, it.message)
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(StuckSubmissionCleanupJob::class.java)
        private val STUCK_STATUSES = setOf(SubmissionStatus.PENDING, SubmissionStatus.DISPATCHED)
        private val OPEN_JOB_STATUSES = setOf(JobStatus.SCHEDULED, JobStatus.RUNNING, JobStatus.RESTARTING)
    }
}
