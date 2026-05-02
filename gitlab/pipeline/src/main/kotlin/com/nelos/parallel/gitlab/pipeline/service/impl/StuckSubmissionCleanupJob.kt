package com.nelos.parallel.gitlab.pipeline.service.impl

import com.nelos.parallel.gitlab.enums.SubmissionStatus
import com.nelos.parallel.gitlab.service.SubmissionService
import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.jobs.service.JobService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime

/**
 * Transitions submissions stuck in PENDING/DISPATCHED beyond [STUCK_TIMEOUT] into ERROR.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component
class StuckSubmissionCleanupJob(
    private val submissionService: SubmissionService,
    private val jobService: JobService,
    private val submissionLogger: SubmissionLogger,
) {

    @Scheduled(fixedDelayString = "PT1M")
    fun cleanup() {
        val cutoff = LocalDateTime.now().minus(STUCK_TIMEOUT)
        val stuck = submissionService.findByStatusIn(STUCK_STATUSES).filter {
            (it.createdAt ?: LocalDateTime.MAX).isBefore(cutoff)
        }
        if (stuck.isEmpty()) return
        LOG.warn("Marking {} stuck submission(s) as ERROR (older than {})", stuck.size, STUCK_TIMEOUT)
        val timeoutMin = STUCK_TIMEOUT.toMinutes()
        // Per-submission try/catch so one DB hiccup on one row doesn't abort
        // the whole batch - the next scheduled tick re-finds the still-stuck
        // submissions and retries.
        stuck.forEach { submission ->
            runCatching {
                submission.status = SubmissionStatus.ERROR
                submission.completedAt = LocalDateTime.now()
                submission.resultSummary = "Engine timeout - no result received within $timeoutMin min"
                submissionService.save(submission)
                submission.jobId?.let { jobId ->
                    jobService.tryFindById(jobId)?.takeIf { it.status in OPEN_JOB_STATUSES }?.let { job ->
                        job.status = JobStatus.ERROR
                        job.endDate = LocalDateTime.now()
                        jobService.save(job)
                    }
                }
                submission.id?.let {
                    submissionLogger.appendOne(it, "[parallel] [TIMEOUT] Submission timed out after $timeoutMin min waiting for runner")
                }
            }.onFailure {
                LOG.warn("Failed to fail-stuck submission {}: {} (will retry on next tick)", submission.id, it.message)
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(StuckSubmissionCleanupJob::class.java)
        private val STUCK_TIMEOUT: Duration = Duration.ofMinutes(30)
        private val STUCK_STATUSES = setOf(SubmissionStatus.PENDING, SubmissionStatus.DISPATCHED)
        private val OPEN_JOB_STATUSES = setOf(JobStatus.SCHEDULED, JobStatus.RUNNING, JobStatus.RESTARTING)
    }
}
