package com.nelos.parallel.pipeline.core.service.impl

import com.nelos.parallel.jobs.entity.Job
import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.jobs.service.JobService
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.data.entity.Submission
import com.nelos.parallel.pipeline.data.service.SubmissionService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class StuckSubmissionCleanupJobTest {

    private val submissionService: SubmissionService = mock()
    private val jobService: JobService = mock()
    private val submissionLogger: SubmissionLogger = mock()
    private val txManager: PlatformTransactionManager = mock {
        // Inline pass-through: any txTemplate.execute { ... } runs the block.
        on { getTransaction(any()) } doReturn SimpleTransactionStatus()
    }

    // Tests pin the timeout at 30 min so existing "30 min" assertions remain
    // independent of the production default (configured via
    // `parallel.submission.stuck-timeout`).
    private val job = StuckSubmissionCleanupJob(
        submissionService, jobService, submissionLogger, Duration.ofMinutes(30), txManager,
    )

    // --- helpers --------------------------------------------------------

    private fun submission(
        id: Long,
        status: SubmissionStatus = SubmissionStatus.PENDING,
        createdAt: LocalDateTime? = LocalDateTime.now().minusHours(1),
        jobId: Long? = null,
    ): Submission = Submission().apply {
        this.id = id
        this.status = status
        this.createdAt = createdAt
        this.jobId = jobId
    }

    private fun jobOf(id: Long, status: JobStatus): Job = Job().apply {
        this.id = id
        this.status = status
    }

    // --- cases ----------------------------------------------------------

    @Test
    fun `does nothing when no stuck submissions exist`() {
        whenever(submissionService.findByStatusIn(any())).thenReturn(emptyList())

        job.cleanup()

        verify(submissionService, never()).save(any<Submission>())
        verify(submissionLogger, never()).appendOne(any(), any())
    }

    @Test
    fun `does nothing when stuck-status submission is younger than the timeout`() {
        // STUCK_TIMEOUT is 30 minutes; this row is only 5 minutes old.
        val young = submission(id = 1L, createdAt = LocalDateTime.now().minusMinutes(5))
        whenever(submissionService.findByStatusIn(any())).thenReturn(listOf(young))

        job.cleanup()

        verify(submissionService, never()).tryFindByIdForUpdate(any())
        verify(submissionService, never()).save(any<Submission>())
    }

    @Test
    fun `marks an old PENDING submission as TIMEOUT and stamps completedAt and summary`() {
        val old = submission(id = STUCK_ID, createdAt = LocalDateTime.now().minusHours(2))
        whenever(submissionService.findByStatusIn(any())).thenReturn(listOf(old))
        whenever(submissionService.tryFindByIdForUpdate(STUCK_ID)).thenReturn(old)

        job.cleanup()

        val saved = argumentCaptor<Submission>()
        verify(submissionService).save(saved.capture())
        assertEquals(SubmissionStatus.TIMEOUT, saved.firstValue.status)
        assertNotNull(saved.firstValue.completedAt)
        assertEquals("Engine timeout - no result received within 30 min", saved.firstValue.resultSummary)
        verify(submissionLogger).appendOne(
            eq(STUCK_ID),
            argThat<String> { contains("[TIMEOUT]") && contains("30 min") })
    }

    @Test
    fun `respects a concurrent callback that finalized the row while we waited for the lock`() {
        // Initial scan finds it PENDING, but by the time we lock the row a callback
        // has already set it to COMPLETED - we must NOT overwrite that with ERROR.
        val staleCandidate = submission(id = STUCK_ID, createdAt = LocalDateTime.now().minusHours(2))
        val refreshed = submission(id = STUCK_ID, status = SubmissionStatus.COMPLETED)
        whenever(submissionService.findByStatusIn(any())).thenReturn(listOf(staleCandidate))
        whenever(submissionService.tryFindByIdForUpdate(STUCK_ID)).thenReturn(refreshed)

        job.cleanup()

        verify(submissionService, never()).save(any<Submission>())
        verify(submissionLogger, never()).appendOne(any(), any())
    }

    @Test
    fun `transitions an open job to ERROR alongside the submission`() {
        val s = submission(id = STUCK_ID, createdAt = LocalDateTime.now().minusHours(2), jobId = JOB_ID)
        whenever(submissionService.findByStatusIn(any())).thenReturn(listOf(s))
        whenever(submissionService.tryFindByIdForUpdate(STUCK_ID)).thenReturn(s)
        whenever(jobService.tryFindByIdForUpdate(JOB_ID)).thenReturn(jobOf(JOB_ID, JobStatus.RUNNING))

        job.cleanup()

        val jobSaved = argumentCaptor<Job>()
        verify(jobService).save(jobSaved.capture())
        assertEquals(JobStatus.ERROR, jobSaved.firstValue.status)
        assertNotNull(jobSaved.firstValue.endDate)
    }

    @Test
    fun `leaves a terminal job untouched even when the submission times out`() {
        val s = submission(id = STUCK_ID, createdAt = LocalDateTime.now().minusHours(2), jobId = JOB_ID)
        whenever(submissionService.findByStatusIn(any())).thenReturn(listOf(s))
        whenever(submissionService.tryFindByIdForUpdate(STUCK_ID)).thenReturn(s)
        // Job is already SUCCESS - open-statuses guard must reject this.
        whenever(jobService.tryFindByIdForUpdate(JOB_ID)).thenReturn(jobOf(JOB_ID, JobStatus.SUCCESS))

        job.cleanup()

        verify(submissionService).save(any<Submission>())
        verify(jobService, never()).save(any<Job>())
    }

    @Test
    fun `failure on one submission does not abort the whole batch`() {
        val firstStuckId = STUCK_ID
        val secondStuckId = STUCK_ID + 1L
        val s1 = submission(id = firstStuckId, createdAt = LocalDateTime.now().minusHours(2))
        val s2 = submission(id = secondStuckId, createdAt = LocalDateTime.now().minusHours(2))
        whenever(submissionService.findByStatusIn(any())).thenReturn(listOf(s1, s2))
        whenever(submissionService.tryFindByIdForUpdate(firstStuckId))
            .doThrow(RuntimeException("db hiccup"))
        whenever(submissionService.tryFindByIdForUpdate(secondStuckId)).thenReturn(s2)

        job.cleanup()

        // s1 threw - but s2 must still have been saved
        val saved = argumentCaptor<Submission>()
        verify(submissionService).save(saved.capture())
        assertEquals(secondStuckId, saved.firstValue.id)
        assertEquals(SubmissionStatus.TIMEOUT, saved.firstValue.status)
    }

    @Test
    fun `submission without an id is skipped without errors`() {
        val noId = Submission().apply {
            status = SubmissionStatus.PENDING
            createdAt = LocalDateTime.now().minusHours(2)
        }
        whenever(submissionService.findByStatusIn(any())).thenReturn(listOf(noId))

        job.cleanup() // must not crash

        verify(submissionService, never()).tryFindByIdForUpdate(any())
    }

    companion object {
        private const val STUCK_ID = 42L
        private const val JOB_ID = 99L
    }
}
