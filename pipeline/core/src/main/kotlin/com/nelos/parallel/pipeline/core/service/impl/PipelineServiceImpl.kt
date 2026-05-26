package com.nelos.parallel.pipeline.core.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.adapter.enums.SourceType
import com.nelos.parallel.commons.adapter.vo.request.ProgressEvent
import com.nelos.parallel.commons.adapter.vo.request.SourceDescriptor
import com.nelos.parallel.commons.adapter.vo.request.TaskSubmission
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.jobs.entity.Job
import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.jobs.service.JobService
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.commons.event.SubmissionTerminalEvent
import com.nelos.parallel.pipeline.commons.service.*
import com.nelos.parallel.pipeline.commons.vo.PipelineStatusResponse
import com.nelos.parallel.pipeline.commons.vo.PipelineSubmitRequest
import com.nelos.parallel.pipeline.commons.vo.PipelineSubmitResponse
import com.nelos.parallel.pipeline.data.entity.Submission
import com.nelos.parallel.pipeline.data.entity.SubmissionResult
import com.nelos.parallel.pipeline.data.service.AssignmentService
import com.nelos.parallel.pipeline.data.service.SubmissionLogService
import com.nelos.parallel.pipeline.data.service.SubmissionResultService
import com.nelos.parallel.pipeline.data.service.SubmissionService
import com.nelos.parallel.pipeline.runner.exception.NoRunnerAvailableException
import com.nelos.parallel.pipeline.runner.service.RunnerContext
import com.nelos.parallel.pipeline.runner.service.RunnerManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.pipelineService")
class PipelineServiceImpl(
    private val assignmentService: AssignmentService,
    private val submissionService: SubmissionService,
    private val submissionResultService: SubmissionResultService,
    private val submissionLogService: SubmissionLogService,
    private val submissionLogger: SubmissionLogger,
    private val jobService: JobService,
    private val studentResolver: StudentResolver,
    private val runnerManager: RunnerManager,
    private val resultEvaluator: SubmissionResultEvaluator,
    private val resultLogFormatter: SubmissionResultLogFormatter,
    private val objectMapper: ObjectMapper,
    @param:Value("\${gitlab.api.token:}") private val gitlabApiToken: String,
    @param:Value("\${gitlab.server.base-url:}") private val serverBaseUrl: String,
    private val applicationEventPublisher: ApplicationEventPublisher,
    transactionManager: PlatformTransactionManager,
) : PipelineService {

    private val txTemplate = TransactionTemplate(transactionManager)

    /**
     * Spring proxy reference to self. Needed so that `submit()` can wire
     * `onResult = self::handleResult` for the runner callbacks - calling
     * `this::handleResult` directly bypasses AOP, which would skip the
     * `@Transactional` on `handleResult` when a sync runner (local/docker)
     * fires the callback from its background pool. `@Lazy` breaks the
     * self-injection cycle at startup.
     */
    @Autowired
    @Lazy
    private lateinit var self: PipelineService

    // --- Submit ----------------------------------------------------------

    /**
     * Submission is split into discrete phases so the slow network calls
     * (HTTP health-check + AMQP submitTask RPC, up to ~30s) and the process
     * spawn for local/docker runners do NOT happen inside the DB transaction -
     * keeping a Hikari connection open during a remote RPC or a process spawn
     * would saturate the small pool under concurrent CI submits.
     *
     *   txTemplate { prepareSubmission }   - DB writes + pessimistic lock to
     *                                        reject any prior pending row for
     *                                        the same MR.
     *   txTemplate { markDispatched }      - DB write only.
     *   runnerManager.dispatch             - outside tx; runner-specific work.
     *   txTemplate { failSubmission }      - DB write only, on any failure.
     */
    override fun submit(request: PipelineSubmitRequest): PipelineSubmitResponse {
        val inputs = resolveInputs(request)
        val ctx = txTemplate.execute {
            prepareSubmission(
                inputs.assignmentCode, inputs.assignmentId, inputs.mrIid, inputs.username,
                inputs.sourceBranch, inputs.solutionRepoUrl, inputs.commitSha,
            )
        } ?: error("prepareSubmission returned null")

        // Failure-mode policy:
        //   - NoRunnerAvailableException (every runner declined) -> `failSubmissionTx`:
        //     no runner accepted the task, no async result is coming.
        //   - Async runners (HTTP/Rabbit) swallow RPC failures inside `tryRun`
        //     and return a handle anyway - the broker may have delivered the
        //     task to the engine before our timeout fired, and the engine
        //     will then callback with a real result. Marking ERROR here
        //     would discard that real result on arrival (handleResult's
        //     terminal-status guard would drop it). StuckSubmissionCleanupJob
        //     handles the genuine "engine never came back" case.
        return try {
            txTemplate.executeWithoutResult { markDispatched(ctx.submissionId) }
            val runnerCtx = RunnerContext(
                submissionId = ctx.submissionId,
                task = buildTask(inputs, ctx.submissionId),
                onResult = self::handleResult,
            )
            val handle = runnerManager.dispatch(runnerCtx)
            bestEffortLog(
                ctx.submissionId,
                "[parallel] Dispatched via runner '${handle.runnerName}' (job=${ctx.jobId})"
            )
            LOG.info(
                "Pipeline submission id={} dispatched via runner {}",
                ctx.submissionId, handle.runnerName,
            )
            PipelineSubmitResponse(submissionId = ctx.submissionId, status = "dispatched")
        } catch (e: NoRunnerAvailableException) {
            failSubmissionTx(ctx, "No runner accepted the task: ${e.message ?: "no runners"}")
        } catch (e: Exception) {
            // markDispatched / unexpected runner failure - safe to mark ERROR,
            // no late callback is possible.
            failSubmissionTx(ctx, "Dispatch failed: ${e.message ?: e::class.simpleName ?: "unknown"}")
        }
    }

    /**
     * Validates the CI-submitted request and resolves the assignment.
     * Required fields missing or a mismatch with the assignment template are
     * caught here, before any DB write - the caller is the CI job, so an
     * `error("...")` bubbling out as 500 fails the pipeline with a useful
     * message.
     */
    private fun resolveInputs(request: PipelineSubmitRequest): ResolvedInputs {
        val projectPath = request.projectPath ?: error("projectPath is required")
        val mrIid = request.mrIid ?: error("mrIid is required")
        val solutionRepoUrl = request.sourceRepoUrl ?: error("sourceRepoUrl is required")
        // Username = the OWNER of the source fork (namespace prefix), NOT the MR author
        // or the pipeline-trigger user - those can be an admin running CI on behalf of
        // a student. Falls back to deriving from sourceRepoUrl when CI didn't supply it.
        val username = request.username?.takeIf { it.isNotBlank() }
            ?: extractNamespaceFromRepoUrl(solutionRepoUrl)
            ?: error("Cannot determine submitter - request.username is blank and sourceRepoUrl '$solutionRepoUrl' has no namespace")

        val assignment = assignmentService.findByGitlabProjectPath(projectPath)
            ?: error("No assignment found for GitLab project path '$projectPath'")
        if (!assignment.active) error("Assignment '${assignment.code}' is inactive")
        val testRepoUrl = assignment.testRepoUrl ?: error("Assignment '${assignment.code}' has no test repo URL")
        val assignmentId = assignment.id ?: error("Assignment has no id")
        val assignmentCode = assignment.code ?: error("Assignment ${assignment.id} has no code")

        return ResolvedInputs(
            assignment = assignment,
            assignmentId = assignmentId,
            assignmentCode = assignmentCode,
            username = username,
            sourceBranch = request.sourceBranch ?: "main",
            solutionRepoUrl = solutionRepoUrl,
            testRepoUrl = testRepoUrl,
            token = gitlabApiToken.ifBlank { null },
            mrIid = mrIid,
            commitSha = request.commitSha,
        )
    }

    /**
     * Build the engine's [TaskSubmission]. Engine-side `jobId == submissionId`
     * so the async result callback maps 1:1 back to a Submission row; the Job
     * entity is kept server-side as the unit a future batch could group over.
     */
    private fun buildTask(inputs: ResolvedInputs, submissionId: Long): TaskSubmission = TaskSubmission(
        jobId = submissionId.toString(),
        testId = inputs.assignment.code,
        solutionSourceType = SourceType.GIT,
        solutionSource = SourceDescriptor.GitSource(
            url = inputs.solutionRepoUrl,
            branch = inputs.sourceBranch,
            token = inputs.token
        ),
        testSourceType = SourceType.GIT,
        testSource = SourceDescriptor.GitSource(
            url = inputs.testRepoUrl,
            branch = inputs.assignment.testRepoBranch,
            token = inputs.token
        ),
        threads = inputs.assignment.threads,
        callbackUrl = "$serverBaseUrl/api/callback/result",
        memoryLimitMb = inputs.assignment.memoryLimitMb,
        wallTimeSec = inputs.assignment.wallTimeSec,
        cpuTimeSec = inputs.assignment.cpuTimeSec,
        maxProcesses = inputs.assignment.maxProcesses,
        warmupIterations = inputs.assignment.warmupIterations,
    )

    /** Bundle of CI-request fields validated against the matching assignment. */
    private data class ResolvedInputs(
        val assignment: com.nelos.parallel.pipeline.data.entity.Assignment,
        val assignmentId: Long,
        val assignmentCode: String,
        val username: String,
        val sourceBranch: String,
        val solutionRepoUrl: String,
        val testRepoUrl: String,
        val token: String?,
        val mrIid: Long,
        val commitSha: String?,
    )

    /**
     * Extracts the GitLab namespace (first path segment) from a repository URL like
     * `http://host/student1/lab1.git` -> `student1`. Used as the canonical student
     * identifier when CI didn't pass an explicit `username`.
     */
    private fun extractNamespaceFromRepoUrl(url: String): String? {
        val withoutScheme = url.substringAfter("://", url)
        val pathStart = withoutScheme.indexOf('/').takeIf { it >= 0 } ?: return null
        val path = withoutScheme.substring(pathStart + 1).trimStart('/')
        val namespace = path.substringBefore('/', "").takeIf { it.isNotBlank() } ?: return null
        return namespace
    }

    /** Best-effort log append - never aborts the calling flow. */
    private fun bestEffortLog(submissionId: Long, line: String) {
        runCatching { submissionLogger.appendOne(submissionId, line) }
            .onFailure { LOG.warn("Failed to append log for submission {}: {}", submissionId, it.message) }
    }

    private data class SubmissionContext(val submissionId: Long, val jobId: Long)

    /**
     * DB-only phase of [submit]. Resolves the student, inserts a fresh
     * [Submission] in `PENDING`, then takes a `PESSIMISTIC_WRITE` lock on
     * any previous pending submission for the same MR and rejects it.
     * Runs inside the [txTemplate] callback.
     */
    private fun prepareSubmission(
        assignmentCode: String,
        assignmentId: Long,
        mrIid: Long,
        username: String,
        sourceBranch: String,
        solutionRepoUrl: String,
        commitSha: String?,
    ): SubmissionContext {
        val studentId = studentResolver.resolveOrAutoCreate(username)

        val job = jobService.save(Job().apply {
            startDate = LocalDateTime.now()
            status = JobStatus.SCHEDULED
            totalCount = 1
        })

        val submission = submissionService.save(Submission().apply {
            this.assignmentId = assignmentId
            this.userId = studentId
            jobId = job.id
            this.mrIid = mrIid
            this.sourceBranch = sourceBranch
            this.solutionRepoUrl = solutionRepoUrl
            this.commitSha = commitSha
            status = SubmissionStatus.PENDING
            createdAt = LocalDateTime.now()
        })
        val newSubmissionId = submission.id ?: error("Submission has no id after save")
        val newJobId = job.id ?: error("Job has no id after save")

        submissionService.findPendingByMrForUpdate(assignmentId, mrIid)?.takeIf { it.id != newSubmissionId }
            ?.let { prev ->
                prev.status = SubmissionStatus.REJECTED
                submissionService.save(prev)
                prev.id?.let {
                    submissionLogger.appendOne(
                        it,
                        "[parallel] [REJECTED] Superseded by submission #$newSubmissionId"
                    )
                }
                LOG.info("Cancelled previous submission id={} for MR !{}", prev.id, mrIid)
            }

        submissionLogger.append(
            newSubmissionId, listOf(
                "[parallel] Submission #$newSubmissionId created for assignment '$assignmentCode'",
                "[parallel] Student: $username, MR: !$mrIid, commit: ${commitSha?.take(8) ?: "?"}",
            )
        )

        return SubmissionContext(newSubmissionId, newJobId)
    }

    private fun markDispatched(submissionId: Long) {
        // SELECT FOR UPDATE so a concurrent stuck-cleanup or handleResult on the
        // same submission blocks until this transition commits.
        val submission = submissionService.tryFindByIdForUpdate(submissionId)
            ?: error("Submission $submissionId vanished between prepare and dispatch")
        submission.status = SubmissionStatus.DISPATCHED
        submissionService.save(submission)
    }

    private fun failSubmissionTx(ctx: SubmissionContext, reason: String): PipelineSubmitResponse {
        // Returns true iff the submission row was actually found and updated. If it was
        // already deleted (rare, e.g. cascade from a removed user), do not append a log
        // line - the FK on prl_submission_log would fail and turn this error path itself
        // into an exception bubbling out as 500 to the CI caller.
        // The tx itself can also throw (DB down, deadlock); swallow it here so the
        // error path doesn't turn into a 500 - the worst case becomes a PENDING row
        // that StuckSubmissionCleanupJob will pick up.
        val updated = runCatching {
            txTemplate.execute<Boolean> {
                // FOR UPDATE so this races safely with handleResult / stuck-cleanup
                // on the same submission. Re-check terminal status after acquiring
                // the lock: a concurrent engine callback may have already marked
                // this submission COMPLETED while we were waiting on the lock.
                val submission = submissionService.tryFindByIdForUpdate(ctx.submissionId) ?: return@execute false
                if (submission.status in TERMINAL_STATUSES) return@execute false
                submission.status = SubmissionStatus.ERROR
                submission.resultSummary = reason
                submission.completedAt = LocalDateTime.now()
                submissionService.save(submission)
                jobService.tryFindByIdForUpdate(ctx.jobId)?.let { job ->
                    job.status = JobStatus.ERROR
                    job.endDate = LocalDateTime.now()
                    jobService.save(job)
                }
                true
            }
        }.onFailure {
            LOG.error("failSubmissionTx tx failed for submission {}: {}", ctx.submissionId, it.message, it)
        }.getOrNull() ?: false
        if (updated) {
            bestEffortLog(ctx.submissionId, "[parallel] ERROR: $reason")
        }
        LOG.error("Submission {} failed: {}", ctx.submissionId, reason)
        return PipelineSubmitResponse(submissionId = ctx.submissionId, status = "error")
    }

    // --- Status ----------------------------------------------------------

    override fun getStatus(submissionId: Long): PipelineStatusResponse {
        val submission = submissionService.tryFindById(submissionId)
            ?: error("Submission $submissionId not found")

        val finished = submission.status in TERMINAL_STATUSES
        val logs = submissionLogService.findBySubmissionId(submissionId).map { it.line ?: "" }
        val result = if (finished) loadStoredResult(submissionId) else null

        return PipelineStatusResponse(
            submissionId = submissionId,
            status = submission.status?.name ?: "UNKNOWN",
            finished = finished,
            success = if (finished) submission.status == SubmissionStatus.COMPLETED else null,
            logs = logs,
            result = result,
            resultSummary = submission.resultSummary,
        )
    }

    private fun loadStoredResult(submissionId: Long): TaskResult? =
        submissionResultService.findBySubmissionId(submissionId)?.resultJson?.let { json ->
            runCatching { objectMapper.readValue(json, TaskResult::class.java) }.getOrNull()
        }

    // --- Result handling (callback target) -------------------------------

    @Transactional
    override fun handleResult(result: TaskResult) {
        val submissionIdLong = result.jobId.toLongOrNull() ?: run {
            LOG.warn("Received TaskResult with non-numeric jobId={}", result.jobId)
            return
        }
        // FOR UPDATE: serialize against StuckSubmissionCleanupJob and any
        // failSubmissionTx running for the same submission. Without the
        // lock, a stuck-cleanup tick reading the row at the same instant
        // could overwrite our COMPLETED status with ERROR.
        val submission = submissionService.tryFindByIdForUpdate(submissionIdLong) ?: run {
            LOG.warn("Received TaskResult for unknown submissionId={}", result.jobId)
            return
        }
        if (submission.status in TERMINAL_STATUSES) {
            LOG.info(
                "Duplicate TaskResult for already-finished submission {} (status={})",
                submission.id, submission.status
            )
            return
        }
        val submissionId = submission.id ?: error("Submission has no id")

        // One evaluation pass produces the submission status, the job status
        // and the human-readable summary. SubmissionResultEvaluator collapses
        // engine "completed" with any failed test into FAILED so the single
        // `success` flag the CI consumes is enough to drive the pipeline
        // verdict.
        // Load assignment-level evaluator script so VerdictExtensions can run it.
        // The default test-count verdict is in SubmissionResultEvaluatorImpl;
        // KTS / Python overrides come in via registered ScriptVerdictExtensions.
        val script = submission.assignmentId
            ?.let { assignmentService.tryFindById(it) }
            ?.evaluatorScript
        val verdict = resultEvaluator.evaluate(EvaluationContext(result, script))
        submission.status = verdict.submissionStatus
        submission.completedAt = LocalDateTime.now()
        submission.resultSummary = verdict.summary
        submissionService.save(submission)

        submission.jobId?.let { jobId ->
            jobService.tryFindByIdForUpdate(jobId)?.let { job ->
                job.endDate = LocalDateTime.now()
                job.status = verdict.jobStatus
                job.processedCount = 1
                jobService.save(job)
            }
        }

        // Append final log lines (preserving existing rich formatting).
        // Best-effort: a log INSERT failure here would otherwise rollback the entire
        // @Transactional and trigger an AMQP requeue loop (status stays DISPATCHED,
        // engine redelivers the same result, log write fails again - forever).
        // The submission's terminal status is the source of truth and must commit
        // regardless of log-table availability.
        val finalLines = mutableListOf<String>()
        finalLines.add("===== Final output =====")
        resultLogFormatter.appendResultLogs(finalLines, result)
        runCatching { submissionLogger.append(submissionId, finalLines) }
            .onFailure { LOG.warn("Failed to append final log lines for submission {}: {}", submissionId, it.message) }

        // Persist the full result (idempotent upsert by submissionId)
        persistSubmissionResult(submissionId, finalLines, result)

        // Best-effort: never let a stray listener exception roll back the
        // submission's terminal status. Production registers no listener so
        // this is a no-op except in tests.
        runCatching {
            applicationEventPublisher.publishEvent(
                SubmissionTerminalEvent(submissionId, submission.status!!)
            )
        }.onFailure { LOG.warn("SubmissionTerminalEvent publish failed for submission {}: {}", submissionId, it.message) }

        LOG.info("Pipeline result for submission {}: {}", submissionId, result.status)
    }

    @Transactional
    override fun handleProgress(event: ProgressEvent) {
        val submissionIdLong = event.jobId.toLongOrNull() ?: return
        val submission = submissionService.tryFindById(submissionIdLong) ?: return
        if (submission.status in TERMINAL_STATUSES) return
        val submissionId = submission.id ?: return

        val line = resultLogFormatter.formatProgress(event)
        submissionLogger.append(submissionId, line.split(Regex("\\r?\\n")))
    }

    private fun persistSubmissionResult(submissionId: Long, logs: List<String>, result: TaskResult) {
        try {
            val existing = submissionResultService.findBySubmissionId(submissionId)
            val entity = existing ?: SubmissionResult().apply {
                this.submissionId = submissionId
                this.createdAt = LocalDateTime.now()
            }
            entity.logText = logs.joinToString("\n")
            entity.resultJson = objectMapper.writeValueAsString(result)
            submissionResultService.save(entity)
        } catch (e: Exception) {
            LOG.error("Failed to persist submission_result for submission {}: {}", submissionId, e.message, e)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PipelineServiceImpl::class.java)

        private val TERMINAL_STATUSES = setOf(
            SubmissionStatus.COMPLETED, SubmissionStatus.FAILED,
            SubmissionStatus.ERROR, SubmissionStatus.REJECTED,
            SubmissionStatus.TIMEOUT,
        )
    }
}
