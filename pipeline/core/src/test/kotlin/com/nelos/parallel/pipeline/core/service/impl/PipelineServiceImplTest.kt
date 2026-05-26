package com.nelos.parallel.pipeline.core.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.adapter.vo.request.ProgressEvent
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.pipeline.runner.exception.NoRunnerAvailableException
import com.nelos.parallel.pipeline.runner.service.RunHandle
import com.nelos.parallel.pipeline.runner.service.RunnerContext
import com.nelos.parallel.pipeline.runner.service.RunnerManager
import com.nelos.parallel.jobs.entity.Job
import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.jobs.service.JobService
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.commons.event.SubmissionTerminalEvent
import com.nelos.parallel.pipeline.commons.service.*
import com.nelos.parallel.pipeline.commons.vo.PipelineSubmitRequest
import com.nelos.parallel.pipeline.data.entity.Assignment
import com.nelos.parallel.pipeline.data.entity.Submission
import com.nelos.parallel.pipeline.data.entity.SubmissionLog
import com.nelos.parallel.pipeline.data.entity.SubmissionResult
import com.nelos.parallel.pipeline.data.service.AssignmentService
import com.nelos.parallel.pipeline.data.service.SubmissionLogService
import com.nelos.parallel.pipeline.data.service.SubmissionResultService
import com.nelos.parallel.pipeline.data.service.SubmissionService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class PipelineServiceImplTest {

    // --- collaborators (all mocked) ------------------------------------

    private val assignmentService: AssignmentService = mock()
    private val submissionService: SubmissionService = mock()
    private val submissionResultService: SubmissionResultService = mock()
    private val submissionLogService: SubmissionLogService = mock()
    private val submissionLogger: SubmissionLogger = mock()
    private val jobService: JobService = mock()
    private val studentResolver: StudentResolver = mock()
    private val runnerManager: RunnerManager = mock()
    private val resultEvaluator: SubmissionResultEvaluator = mock()
    private val resultLogFormatter = SubmissionResultLogFormatter()
    private val objectMapper = ObjectMapper()
    private val txManager: PlatformTransactionManager = mock {
        on { getTransaction(any()) } doReturn SimpleTransactionStatus()
    }
    private val applicationEventPublisher: ApplicationEventPublisher = mock()

    private lateinit var pipeline: PipelineServiceImpl

    @BeforeEach
    fun setUp() {
        pipeline = PipelineServiceImpl(
            assignmentService, submissionService, submissionResultService,
            submissionLogService, submissionLogger, jobService,
            studentResolver, runnerManager, resultEvaluator, resultLogFormatter,
            objectMapper, "gitlab-token", "http://server",
            applicationEventPublisher, txManager,
        )
        // Production wires `self` via `@Autowired @Lazy`. In a plain unit test
        // we set it to the same instance so `self::handleResult` references
        // a real method (callers don't actually invoke it in submit() tests,
        // but the field has to be assigned before use).
        val selfField = PipelineServiceImpl::class.java.getDeclaredField("self")
        selfField.isAccessible = true
        selfField.set(pipeline, pipeline)
    }

    // --- builders ------------------------------------------------------

    private fun assignment(
        id: Long = 100L,
        code: String = "lab1",
        gitlabProjectPath: String = "group/lab1",
        testRepoUrl: String? = "http://gitlab/tests/lab1.git",
        active: Boolean = true,
    ) = Assignment().apply {
        this.id = id
        this.code = code
        this.gitlabProjectPath = gitlabProjectPath
        this.testRepoUrl = testRepoUrl
        this.active = active
    }

    private fun submitRequest(
        projectPath: String? = "group/lab1",
        mrIid: Long? = 7L,
        sourceRepoUrl: String? = "http://gitlab/student1/lab1.git",
        sourceBranch: String? = "feature",
        commitSha: String? = "abcdef123",
        username: String? = "student1",
    ) = PipelineSubmitRequest(
        projectPath = projectPath, mrIid = mrIid, sourceRepoUrl = sourceRepoUrl,
        sourceBranch = sourceBranch, commitSha = commitSha, username = username,
    )

    private fun savedSubmission(id: Long = 500L): Submission = Submission().apply {
        this.id = id
        status = SubmissionStatus.PENDING
    }

    private fun savedJob(id: Long = 600L): Job = Job().apply {
        this.id = id
        status = JobStatus.SCHEDULED
    }

    /** Configures the standard "happy path" mock answers for [submit]. */
    private fun setupHappyPath(submissionId: Long = 500L, jobId: Long = 600L): Submission {
        val savedSub = Submission().apply { this.id = submissionId; status = SubmissionStatus.PENDING }
        val savedJob = Job().apply { this.id = jobId; status = JobStatus.SCHEDULED }
        whenever(assignmentService.findByGitlabProjectPath(any())).thenReturn(assignment())
        whenever(studentResolver.resolveOrAutoCreate(any())).thenReturn(42L)
        whenever(jobService.save(any<Job>())).thenReturn(savedJob)
        whenever(submissionService.save(any<Submission>())).thenReturn(savedSub)
        whenever(submissionService.findPendingByMrForUpdate(any(), any())).thenReturn(null)
        // markDispatched / failSubmissionTx re-read the submission under a lock.
        whenever(submissionService.tryFindByIdForUpdate(submissionId)).thenReturn(savedSub)
        return savedSub
    }

    /** Mock RunHandle - the manager hands one back to PipelineService on a successful dispatch. */
    private fun handle(runnerName: String = "http", jobId: String = "500"): RunHandle = object : RunHandle {
        override val runnerName: String = runnerName
        override val jobId: String = jobId
        override fun cancel() = Unit
    }

    // --- input validation ----------------------------------------------

    @Nested
    inner class InputValidation {

        @Test
        fun `missing projectPath bubbles out as IllegalStateException`() {
            val ex = assertThrows<IllegalStateException> {
                pipeline.submit(submitRequest(projectPath = null))
            }
            assertTrue(ex.message?.contains("projectPath") == true)
        }

        @Test
        fun `missing mrIid bubbles out`() {
            assertThrows<IllegalStateException> {
                pipeline.submit(submitRequest(mrIid = null))
            }
        }

        @Test
        fun `missing sourceRepoUrl bubbles out`() {
            assertThrows<IllegalStateException> {
                pipeline.submit(submitRequest(sourceRepoUrl = null))
            }
        }

        @Test
        fun `blank username with no namespace in URL bubbles out`() {
            // URL with no path -> no namespace to extract -> error
            assertThrows<IllegalStateException> {
                pipeline.submit(submitRequest(username = "", sourceRepoUrl = "http://gitlab"))
            }
        }

        @Test
        fun `blank username falls back to namespace extracted from sourceRepoUrl`() {
            setupHappyPath()
            whenever(runnerManager.dispatch(any<RunnerContext>())).thenReturn(handle())

            pipeline.submit(submitRequest(username = "", sourceRepoUrl = "http://gitlab/myuser/lab1.git"))

            verify(studentResolver).resolveOrAutoCreate("myuser")
        }

        @Test
        fun `unknown assignment bubbles out`() {
            whenever(assignmentService.findByGitlabProjectPath(any())).thenReturn(null)

            val ex = assertThrows<IllegalStateException> { pipeline.submit(submitRequest()) }
            assertTrue(ex.message?.contains("No assignment found") == true)
        }

        @Test
        fun `inactive assignment bubbles out`() {
            whenever(assignmentService.findByGitlabProjectPath(any()))
                .thenReturn(assignment(active = false))

            val ex = assertThrows<IllegalStateException> { pipeline.submit(submitRequest()) }
            assertTrue(ex.message?.contains("inactive") == true)
        }

        @Test
        fun `assignment without testRepoUrl bubbles out`() {
            whenever(assignmentService.findByGitlabProjectPath(any()))
                .thenReturn(assignment(testRepoUrl = null))

            val ex = assertThrows<IllegalStateException> { pipeline.submit(submitRequest()) }
            assertTrue(ex.message?.contains("test repo URL") == true)
        }
    }

    // --- submit() flow -------------------------------------------------

    @Nested
    inner class Submit {

        @Test
        fun `happy path dispatches and returns dispatched status`() {
            setupHappyPath()
            whenever(runnerManager.dispatch(any<RunnerContext>())).thenReturn(handle())

            val response = pipeline.submit(submitRequest())

            assertEquals(500L, response.submissionId)
            assertEquals("dispatched", response.status)
        }

        @Test
        fun `no runner triggers failSubmissionTx and returns error`() {
            setupHappyPath()
            whenever(runnerManager.dispatch(any<RunnerContext>()))
                .thenThrow(NoRunnerAvailableException(emptyList()))

            val response = pipeline.submit(submitRequest())

            assertEquals("error", response.status)
            // failSubmissionTx should have re-locked the submission and tried to save it as ERROR.
            val saved = argumentCaptor<Submission>()
            verify(submissionService, atLeastOnce()).save(saved.capture())
            assertTrue(
                saved.allValues.any { it.status == SubmissionStatus.ERROR },
                "expected at least one save() with status=ERROR but got ${saved.allValues.map { it.status }}",
            )
        }

        @Test
        fun `runner that accepts despite a swallowed RPC failure still reports dispatched`() {
            // HttpTaskRunner / RabbitTaskRunner swallow submitTask exceptions and return a
            // handle anyway, on the bet that the engine still received the message and will
            // callback. The pipeline must treat this the same as a clean dispatch.
            setupHappyPath()
            whenever(runnerManager.dispatch(any<RunnerContext>())).thenReturn(handle())

            val response = pipeline.submit(submitRequest())

            assertEquals("dispatched", response.status)
            val saved = argumentCaptor<Submission>()
            verify(submissionService, atLeastOnce()).save(saved.capture())
            assertTrue(
                saved.allValues.none { it.status == SubmissionStatus.ERROR },
                "swallowed RPC failure must not flip submission to ERROR (stuck-cleanup handles it).",
            )
        }

        @Test
        fun `previous pending submission for the same MR is rejected`() {
            setupHappyPath()
            whenever(runnerManager.dispatch(any<RunnerContext>())).thenReturn(handle())
            val prev = Submission().apply {
                id = 499L
                status = SubmissionStatus.PENDING
            }
            whenever(submissionService.findPendingByMrForUpdate(any(), any())).thenReturn(prev)

            pipeline.submit(submitRequest())

            // The previous one must have been saved with REJECTED.
            val saved = argumentCaptor<Submission>()
            verify(submissionService, atLeastOnce()).save(saved.capture())
            assertTrue(
                saved.allValues.any { it.id == 499L && it.status == SubmissionStatus.REJECTED },
                "expected previous submission flipped to REJECTED",
            )
        }
    }

    // --- handleResult() ------------------------------------------------

    @Nested
    inner class HandleResult {

        @Test
        fun `non-numeric jobId is ignored without side effects`() {
            pipeline.handleResult(TaskResult(jobId = "not-a-number", status = "completed"))

            verify(submissionService, never()).tryFindByIdForUpdate(any())
        }

        @Test
        fun `unknown submission is ignored without side effects`() {
            whenever(submissionService.tryFindByIdForUpdate(any())).thenReturn(null)

            pipeline.handleResult(TaskResult(jobId = "777", status = "completed"))

            verify(submissionService, never()).save(any<Submission>())
        }

        @Test
        fun `duplicate result for already-finished submission is ignored`() {
            val terminal = Submission().apply {
                id = 1L
                status = SubmissionStatus.COMPLETED
            }
            whenever(submissionService.tryFindByIdForUpdate(1L)).thenReturn(terminal)

            pipeline.handleResult(TaskResult(jobId = "1", status = "completed"))

            verify(submissionService, never()).save(any<Submission>())
            verify(resultEvaluator, never()).evaluate(any())
        }

        @Test
        fun `late result for a TIMEOUT submission is dropped (TIMEOUT is terminal)`() {
            // Stuck-cleanup may have already marked this submission TIMEOUT while
            // the engine was about to callback. The callback handler must not
            // resurrect the row by writing a fresh status on top of TIMEOUT.
            val timedOut = Submission().apply {
                id = 1L
                status = SubmissionStatus.TIMEOUT
            }
            whenever(submissionService.tryFindByIdForUpdate(1L)).thenReturn(timedOut)

            pipeline.handleResult(TaskResult(jobId = "1", status = "completed"))

            verify(submissionService, never()).save(any<Submission>())
            verify(resultEvaluator, never()).evaluate(any())
        }

        @Test
        fun `normal completion saves submission + job and persists result`() {
            val s = Submission().apply {
                id = 1L
                status = SubmissionStatus.DISPATCHED
                jobId = 11L
                assignmentId = 100L
            }
            whenever(submissionService.tryFindByIdForUpdate(1L)).thenReturn(s)
            whenever(assignmentService.tryFindById(100L)).thenReturn(assignment())
            whenever(jobService.tryFindByIdForUpdate(11L)).thenReturn(savedJob(id = 11L))
            whenever(submissionResultService.findBySubmissionId(1L)).thenReturn(null)
            whenever(resultEvaluator.evaluate(any())).thenReturn(
                SubmissionVerdict(
                    submissionStatus = SubmissionStatus.COMPLETED,
                    jobStatus = JobStatus.SUCCESS,
                    summary = "all good",
                ),
            )

            pipeline.handleResult(TaskResult(jobId = "1", status = "completed"))

            val savedSubs = argumentCaptor<Submission>()
            verify(submissionService).save(savedSubs.capture())
            assertEquals(SubmissionStatus.COMPLETED, savedSubs.firstValue.status)
            assertEquals("all good", savedSubs.firstValue.resultSummary)
            assertTrue(savedSubs.firstValue.completedAt != null)

            val savedJobs = argumentCaptor<Job>()
            verify(jobService).save(savedJobs.capture())
            assertEquals(JobStatus.SUCCESS, savedJobs.firstValue.status)

            verify(submissionResultService).save(any<SubmissionResult>())
            verify(submissionLogger).append(eq(1L), any())
        }

        @Test
        fun `log append failure does not propagate out of handleResult`() {
            val s = Submission().apply {
                id = 1L
                status = SubmissionStatus.DISPATCHED
            }
            whenever(submissionService.tryFindByIdForUpdate(1L)).thenReturn(s)
            whenever(resultEvaluator.evaluate(any())).thenReturn(
                SubmissionVerdict(
                    submissionStatus = SubmissionStatus.COMPLETED,
                    jobStatus = JobStatus.SUCCESS,
                    summary = "ok",
                ),
            )
            whenever(submissionLogger.append(any(), any())).doThrow(RuntimeException("log table dropped"))

            pipeline.handleResult(TaskResult(jobId = "1", status = "completed"))

            // submission still saved
            verify(submissionService).save(any<Submission>())
            // and we still attempted to persist the full result
            verify(submissionResultService).save(any<SubmissionResult>())
        }

        @Test
        fun `persistSubmissionResult writes serialized json including the engine status`() {
            val s = Submission().apply {
                id = 1L
                status = SubmissionStatus.DISPATCHED
            }
            whenever(submissionService.tryFindByIdForUpdate(1L)).thenReturn(s)
            whenever(resultEvaluator.evaluate(any())).thenReturn(
                SubmissionVerdict(SubmissionStatus.COMPLETED, JobStatus.SUCCESS, "ok"),
            )

            pipeline.handleResult(TaskResult(jobId = "1", status = "completed", error = "synthetic"))

            val captured = argumentCaptor<SubmissionResult>()
            verify(submissionResultService).save(captured.capture())
            assertTrue(captured.firstValue.resultJson?.contains("\"status\":\"completed\"") == true)
            assertTrue(captured.firstValue.resultJson?.contains("\"error\":\"synthetic\"") == true)
        }

        @Test
        fun `publishes SubmissionTerminalEvent with submissionId and terminal status`() {
            val s = Submission().apply {
                id = 1L
                status = SubmissionStatus.DISPATCHED
            }
            whenever(submissionService.tryFindByIdForUpdate(1L)).thenReturn(s)
            whenever(resultEvaluator.evaluate(any())).thenReturn(
                SubmissionVerdict(SubmissionStatus.FAILED, JobStatus.FAILED, "two scenarios failed"),
            )

            pipeline.handleResult(TaskResult(jobId = "1", status = "completed"))

            val captured = argumentCaptor<SubmissionTerminalEvent>()
            verify(applicationEventPublisher).publishEvent(captured.capture())
            assertEquals(1L, captured.firstValue.submissionId)
            assertEquals(SubmissionStatus.FAILED, captured.firstValue.status)
        }
    }

    // --- handleProgress() ----------------------------------------------

    @Nested
    inner class HandleProgress {

        @Test
        fun `non-numeric jobId is ignored`() {
            pipeline.handleProgress(ProgressEvent(jobId = "boom", phase = "test"))

            verify(submissionService, never()).tryFindById(any())
        }

        @Test
        fun `unknown submission is ignored`() {
            whenever(submissionService.tryFindById(any())).thenReturn(null)

            pipeline.handleProgress(ProgressEvent(jobId = "7", phase = "test"))

            verify(submissionLogger, never()).append(any(), any())
        }

        @Test
        fun `terminal submission status drops progress events`() {
            val s = Submission().apply {
                id = 7L
                status = SubmissionStatus.COMPLETED
            }
            whenever(submissionService.tryFindById(7L)).thenReturn(s)

            pipeline.handleProgress(ProgressEvent(jobId = "7", phase = "test"))

            verify(submissionLogger, never()).append(any(), any())
        }

        @Test
        fun `live submission appends a formatted progress line`() {
            val s = Submission().apply {
                id = 7L
                status = SubmissionStatus.DISPATCHED
            }
            whenever(submissionService.tryFindById(7L)).thenReturn(s)

            pipeline.handleProgress(
                ProgressEvent(jobId = "7", phase = "resolveTests", message = "cloning"),
            )

            val captor = argumentCaptor<List<String>>()
            verify(submissionLogger).append(eq(7L), captor.capture())
            assertTrue(captor.firstValue.any { it.contains("[parallel] [resolveTests]: cloning") })
        }
    }

    // --- getStatus() ---------------------------------------------------

    @Nested
    inner class GetStatus {

        @Test
        fun `unknown submission bubbles out`() {
            whenever(submissionService.tryFindById(any())).thenReturn(null)

            assertThrows<IllegalStateException> { pipeline.getStatus(1L) }
        }

        @Test
        fun `pending submission returns finished=false with logs and null result`() {
            val s = Submission().apply {
                id = 1L
                status = SubmissionStatus.PENDING
                resultSummary = "still working"
            }
            whenever(submissionService.tryFindById(1L)).thenReturn(s)
            whenever(submissionLogService.findBySubmissionId(1L)).thenReturn(
                listOf(SubmissionLog().apply { line = "line1" }, SubmissionLog().apply { line = "line2" }),
            )

            val resp = pipeline.getStatus(1L)

            assertEquals(false, resp.finished)
            assertNull(resp.success)
            assertNull(resp.result)
            assertEquals(listOf("line1", "line2"), resp.logs)
            assertEquals("still working", resp.resultSummary)
        }

        @Test
        fun `completed submission deserializes stored TaskResult and sets success=true`() {
            val s = Submission().apply {
                id = 1L
                status = SubmissionStatus.COMPLETED
            }
            val storedJson = objectMapper.writeValueAsString(
                TaskResult(jobId = "1", status = "completed", durationMs = 50.0),
            )
            whenever(submissionService.tryFindById(1L)).thenReturn(s)
            whenever(submissionLogService.findBySubmissionId(1L)).thenReturn(emptyList())
            whenever(submissionResultService.findBySubmissionId(1L)).thenReturn(
                SubmissionResult().apply { resultJson = storedJson },
            )

            val resp = pipeline.getStatus(1L)

            assertEquals(true, resp.finished)
            assertEquals(true, resp.success)
            assertEquals("completed", resp.result?.status)
        }

        @Test
        fun `FAILED is finished but success=false`() {
            val s = Submission().apply { id = 1L; status = SubmissionStatus.FAILED }
            whenever(submissionService.tryFindById(1L)).thenReturn(s)
            whenever(submissionLogService.findBySubmissionId(1L)).thenReturn(emptyList())

            val resp = pipeline.getStatus(1L)

            assertEquals(true, resp.finished)
            assertEquals(false, resp.success)
        }

        @Test
        fun `terminal status with no stored result returns finished=true and null result`() {
            val s = Submission().apply { id = 1L; status = SubmissionStatus.ERROR }
            whenever(submissionService.tryFindById(1L)).thenReturn(s)
            whenever(submissionLogService.findBySubmissionId(1L)).thenReturn(emptyList())
            whenever(submissionResultService.findBySubmissionId(1L)).thenReturn(null)

            val resp = pipeline.getStatus(1L)

            assertEquals(true, resp.finished)
            assertNull(resp.result)
        }
    }
}
