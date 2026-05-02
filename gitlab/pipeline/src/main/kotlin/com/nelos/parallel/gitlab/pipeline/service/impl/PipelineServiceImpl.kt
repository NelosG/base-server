package com.nelos.parallel.gitlab.pipeline.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.NodeAdapterRegistry
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.enums.AdapterStatus
import com.nelos.parallel.commons.adapter.enums.SourceType
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.service.NodeService
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.request.ProgressEvent
import com.nelos.parallel.commons.adapter.vo.request.SourceDescriptor
import com.nelos.parallel.commons.adapter.vo.request.TaskSubmission
import com.nelos.parallel.commons.adapter.vo.response.ScenarioResult
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.commons.adapter.vo.response.TestRun
import com.nelos.parallel.gitlab.entity.Submission
import com.nelos.parallel.gitlab.entity.SubmissionResult
import com.nelos.parallel.gitlab.enums.SubmissionStatus
import com.nelos.parallel.gitlab.pipeline.service.PipelineService
import com.nelos.parallel.gitlab.pipeline.vo.PipelineStatusResponse
import com.nelos.parallel.gitlab.pipeline.vo.PipelineSubmitRequest
import com.nelos.parallel.gitlab.pipeline.vo.PipelineSubmitResponse
import com.nelos.parallel.gitlab.service.AssignmentService
import com.nelos.parallel.gitlab.service.GitlabStudentResolver
import com.nelos.parallel.gitlab.service.SubmissionLogService
import com.nelos.parallel.gitlab.service.SubmissionResultService
import com.nelos.parallel.gitlab.service.SubmissionService
import com.nelos.parallel.jobs.entity.Job
import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.jobs.service.JobService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
    private val studentResolver: GitlabStudentResolver,
    private val nodeRegistry: NodeRegistry,
    private val nodeService: NodeService,
    private val adapterRegistry: NodeAdapterRegistry,
    private val objectMapper: ObjectMapper,
    @param:Value("\${gitlab.api.token:}") private val gitlabApiToken: String,
    @param:Value("\${gitlab.server.base-url:}") private val serverBaseUrl: String,
    transactionManager: PlatformTransactionManager,
) : PipelineService {

    private val txTemplate = TransactionTemplate(transactionManager)

    // --- Submit ----------------------------------------------------------

    /**
     * Submission is split into discrete phases so the slow network calls
     * ([selectRunner] HTTP health-check + [NodeAdapter.submitTask] AMQP RPC,
     * up to ~30s) do NOT happen inside the DB transaction - keeping a Hikari
     * connection open during a remote RPC would saturate the small pool under
     * concurrent CI submits.
     *
     *   txTemplate { prepareSubmission }   - DB writes + pessimistic lock to
     *                                        reject any prior pending row for
     *                                        the same MR.
     *   selectRunner                       - outside tx; HTTP health-check.
     *   txTemplate { markDispatched }      - DB write only.
     *   adapter.submitTask                 - outside tx; AMQP RPC.
     *   txTemplate { failSubmission }      - DB write only, on any failure.
     */
    override fun submit(request: PipelineSubmitRequest): PipelineSubmitResponse {
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
        val sourceBranch = request.sourceBranch ?: "main"
        val token = gitlabApiToken.ifBlank { null }
        val assignmentId = assignment.id ?: error("Assignment has no id")

        val assignmentCode = assignment.code ?: error("Assignment ${assignment.id} has no code")
        val ctx = txTemplate.execute { prepareSubmission(assignmentCode, assignmentId, mrIid, username, sourceBranch, solutionRepoUrl, request.commitSha) }
            ?: error("prepareSubmission returned null")

        // Failure-mode policy:
        //   - Pre-dispatch failures (selectRunner / markDispatched) -> `failSubmissionTx`:
        //     we never reached the engine, no async result is coming.
        //   - submitTask RPC failures AFTER markDispatched -> leave DISPATCHED.
        //     The RPC sees the acceptance ack, not the result. A timed-out ack
        //     does NOT mean the engine rejected the job - broker might have
        //     delivered the task to the engine before our timeout fired, and
        //     the engine will then callback with a real result. Marking ERROR
        //     here would discard that real result on arrival (handleResult's
        //     terminal-status guard would drop it). StuckSubmissionCleanupJob
        //     handles the genuine "engine never came back" case.
        return try {
            val (node, adapter, transportType) = selectRunner(ctx.submissionId)
                ?: return failSubmissionTx(ctx, "No engine nodes available")

            LOG.info("Dispatching to node '{}' via {}", node.nodeId, transportType)
            bestEffortLog(ctx.submissionId, "[parallel] Dispatching to engine node '${node.nodeId}' via $transportType...")

            val task = TaskSubmission(
                // Engine-side jobId = submissionId so callbacks map 1:1 to a Submission.
                // Job entity is kept server-side as the unit a future batch could group over.
                jobId = ctx.submissionId.toString(),
                testId = assignment.code,
                solutionSourceType = SourceType.GIT,
                solutionSource = SourceDescriptor.GitSource(url = solutionRepoUrl, branch = sourceBranch, token = token),
                testSourceType = SourceType.GIT,
                testSource = SourceDescriptor.GitSource(url = testRepoUrl, branch = assignment.testRepoBranch, token = token),
                threads = assignment.threads,
                callbackUrl = "$serverBaseUrl/api/callback/result",
                memoryLimitMb = assignment.memoryLimitMb,
                wallTimeSec = assignment.wallTimeSec,
                cpuTimeSec = assignment.cpuTimeSec,
                maxProcesses = assignment.maxProcesses,
            )

            txTemplate.executeWithoutResult { markDispatched(ctx.submissionId) }

            try {
                adapter.submitTask(node, task)
                bestEffortLog(ctx.submissionId, "[parallel] Task dispatched (job=${ctx.jobId})")
                LOG.info("Pipeline submission id={} dispatched to node {}", ctx.submissionId, node.nodeId)
            } catch (rpcEx: Exception) {
                // Acceptance ack didn't arrive - engine may or may not have the job.
                // Stay in DISPATCHED; let the engine callback (or stuck-cleanup) decide the final state.
                LOG.warn(
                    "Submit RPC to node '{}' failed for submission {} - leaving DISPATCHED so a late " +
                            "engine callback can still be applied: {}",
                    node.nodeId, ctx.submissionId, rpcEx.message,
                )
                bestEffortLog(
                    ctx.submissionId,
                    "[parallel] Submit RPC to '${node.nodeId}' failed: ${rpcEx.message ?: rpcEx::class.simpleName}. " +
                            "If the engine still received the task, the real result will follow; otherwise " +
                            "stuck-submission cleanup will fail this submission.",
                )
            }
            PipelineSubmitResponse(submissionId = ctx.submissionId, status = "dispatched")
        } catch (e: Exception) {
            // selectRunner / markDispatched (or earlier) blew up. We never got to submitTask
            // -> safe to mark ERROR, no late callback is possible.
            failSubmissionTx(ctx, "Dispatch failed: ${e.message ?: e::class.simpleName ?: "unknown"}")
        }
    }

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
        val student = studentResolver.resolveOrAutoCreate(username)
        val studentId = student.id ?: error("Student has no id after resolve/create")

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

        submissionService.findPendingByMrForUpdate(assignmentId, mrIid)?.takeIf { it.id != newSubmissionId }?.let { prev ->
            prev.status = SubmissionStatus.REJECTED
            submissionService.save(prev)
            prev.id?.let { submissionLogger.appendOne(it, "[parallel] [REJECTED] Superseded by submission #$newSubmissionId") }
            LOG.info("Cancelled previous submission id={} for MR !{}", prev.id, mrIid)
        }

        submissionLogger.append(newSubmissionId, listOf(
            "[parallel] Submission #$newSubmissionId created for assignment '$assignmentCode'",
            "[parallel] Student: $username, MR: !$mrIid, commit: ${commitSha?.take(8) ?: "?"}",
        ))

        return SubmissionContext(newSubmissionId, newJobId)
    }

    private fun markDispatched(submissionId: Long) {
        val submission = submissionService.tryFindById(submissionId)
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
                val submission = submissionService.tryFindById(ctx.submissionId) ?: return@execute false
                submission.status = SubmissionStatus.ERROR
                submission.resultSummary = reason
                submission.completedAt = LocalDateTime.now()
                submissionService.save(submission)
                jobService.tryFindById(ctx.jobId)?.let { job ->
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

    /**
     * Selects an engine node. For HTTP transport performs lazy validation: pings every
     * registered node, deletes dead ones in one batch, returns the first responsive
     * runner. AMQP nodes are passed through (Rabbit handles fan-out).
     */
    private fun selectRunner(submissionId: Long): Triple<NodeInfo, NodeAdapter, TransportType>? {
        // First attempt - likely from the registry's TTL cache.
        selectRunnerOnce(submissionId)?.let { return it }
        // No live node found via cached snapshot - could be stale (node died
        // externally / restarted with a new transport). Drop the cache and try
        // once more against fresh DB state. After this second try we give up.
        nodeRegistry.invalidateCache()
        return selectRunnerOnce(submissionId)
    }

    private fun selectRunnerOnce(submissionId: Long): Triple<NodeInfo, NodeAdapter, TransportType>? {
        val nodes = nodeRegistry.findAll()
        if (nodes.isEmpty()) return null

        val httpNodes = mutableListOf<NodeInfo>()
        val amqpNodes = mutableListOf<NodeInfo>()
        nodes.forEach { node ->
            node.transports
                ?.filter { it.config != null && it.status !in setOf(AdapterStatus.STOPPED, AdapterStatus.FAILED) }
                ?.forEach { t ->
                    when (t.type) {
                        TransportType.HTTP -> httpNodes.add(node)
                        TransportType.AMQP -> amqpNodes.add(node)
                    }
                }
        }

        // Prefer AMQP - Rabbit picks live runner from the shared queue
        val amqpAdapter = adapterRegistry.findAdapter(TransportType.AMQP)
        if (amqpNodes.isNotEmpty() && amqpAdapter != null) {
            return Triple(amqpNodes.first(), amqpAdapter, TransportType.AMQP)
        }

        // Fall back to HTTP with lazy ping
        val httpAdapter = adapterRegistry.findAdapter(TransportType.HTTP) ?: return null
        val deadIds = mutableListOf<Long>()
        var live: NodeInfo? = null
        for (candidate in httpNodes) {
            if (httpAdapter.healthCheck(candidate)) {
                live = candidate
                break
            }
            nodeService.findByNodeId(candidate.nodeId)?.id?.let { deadIds.add(it) }
        }
        if (deadIds.isNotEmpty()) {
            val removed = nodeService.deleteByIds(deadIds)
            LOG.info("Lazy validation removed {} dead HTTP node(s)", removed)
            submissionLogger.appendOne(submissionId, "[parallel] Removed $removed unresponsive HTTP node(s)")
            // We bypassed DbNodeRegistry for the DELETE, so its TTL cache still has the stale rows.
            nodeRegistry.invalidateCache()
        }
        return live?.let { Triple(it, httpAdapter, TransportType.HTTP) }
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
        val submission = submissionService.tryFindById(submissionIdLong) ?: run {
            LOG.warn("Received TaskResult for unknown submissionId={}", result.jobId)
            return
        }
        if (submission.status in TERMINAL_STATUSES) {
            LOG.info("Duplicate TaskResult for already-finished submission {} (status={})",
                submission.id, submission.status)
            return
        }
        val submissionId = submission.id ?: error("Submission has no id")

        // Update submission + job
        submission.status = mapResultStatus(result.status)
        submission.completedAt = LocalDateTime.now()
        submission.resultSummary = buildResultSummary(result)
        submissionService.save(submission)

        submission.jobId?.let { jobId ->
            jobService.tryFindById(jobId)?.let { job ->
                job.endDate = LocalDateTime.now()
                job.status = mapJobStatus(result.status)
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
        appendResultLogs(finalLines, result)
        runCatching { submissionLogger.append(submissionId, finalLines) }
            .onFailure { LOG.warn("Failed to append final log lines for submission {}: {}", submissionId, it.message) }

        // Persist the full result (idempotent upsert by submissionId)
        persistSubmissionResult(submissionId, finalLines, result)
        LOG.info("Pipeline result for submission {}: {}", submissionId, result.status)
    }

    @Transactional
    override fun handleProgress(event: ProgressEvent) {
        val submissionIdLong = event.jobId.toLongOrNull() ?: return
        val submission = submissionService.tryFindById(submissionIdLong) ?: return
        if (submission.status in TERMINAL_STATUSES) return
        val submissionId = submission.id ?: return

        val line = formatProgress(event)
        submissionLogger.append(submissionId, line.split(Regex("\\r?\\n")))
    }

    private fun formatProgress(event: ProgressEvent): String {
        if (event.phase == "test") {
            val parts = mutableListOf<String>()
            event.scenario?.let { parts.add(it) }
            event.test?.let { parts.add(it) }
            event.threadCount?.let { parts.add("${it}T") }
            val tag = parts.joinToString("/")
            val time = event.timeMs?.let { " (${it.toLong()}ms)" } ?: ""
            val msg = event.message?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
            return "[parallel] [test:${event.status ?: "?"}] $tag$time$msg"
        }
        val msg = event.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
        return "[parallel] [${event.phase}]$msg"
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

    // --- Log Formatting --------------------------------------------------

    private fun appendResultLogs(logs: MutableList<String>, r: TaskResult) {
        fun line(s: String) = logs.add(s)
        fun section(title: String) { line(""); line("--- $title ---") }

        line("")
        line("============================================")
        line("  Test execution finished: ${r.status.uppercase()}")
        line("============================================")

        r.solution?.let { line("[info] Solution: $it") }
        r.nodeId?.let { line("[info] Engine node: $it") }
        r.durationMs?.let { line("[info] Total duration: %.0fms".format(it)) }
        r.environment?.let { env ->
            val parts = listOfNotNull(
                env.platform?.let { "platform=$it" },
                env.hardwareThreads?.let { "hw_threads=$it" },
            )
            if (parts.isNotEmpty()) line("[env] ${parts.joinToString(", ")}")
        }
        r.effectiveParams?.let { p ->
            val parts = listOfNotNull(
                p.mode?.let { "mode=$it" },
                p.threads?.let { "threads=$it" },
                p.memoryLimitMb?.let { "memory=${it}MB" },
                p.wallTimeSec?.let { "wall_time=${it}s" },
                p.cpuTimeSec?.let { "cpu_time=${it}s" },
            )
            if (parts.isNotEmpty()) line("[params] ${parts.joinToString(", ")}")
        }
        r.assignmentConfig?.let { c ->
            val parts = listOfNotNull(
                c.name?.let { "name=$it" },
                c.framework?.let { "framework=$it" },
                c.mode?.let { "mode=$it" },
                c.correctnessMode?.let { "correctness=$it" },
                c.allowedFrameworks?.takeIf { it.isNotEmpty() }?.let { "allowedFrameworks=${it.joinToString("/")}" },
                c.allowedPackages?.takeIf { it.isNotEmpty() }?.let { "allowedPackages=${it.joinToString("/")}" },
            )
            if (parts.isNotEmpty()) line("[config] ${parts.joinToString(", ")}")
        }

        r.buildInfo?.let { b ->
            b.pluginLoadError?.let { line("[build] Plugin load error: $it") }
        }

        r.pipeline?.let { steps ->
            section("Pipeline Steps")
            steps.forEach { s ->
                val icon = if (s.status == "ok") "✓" else "✗"
                line("  $icon ${s.step} - ${s.status} (%.0fms)".format(s.durationMs ?: 0.0))
            }
            r.totalTimeMs?.let { line("  Total: %.0fms".format(it)) }
        }

        r.failedStep?.let { step ->
            section("FAILURE at step: $step")
            STEP_HINTS[step]?.let { line("[hint] $it") }
            r.error?.let { line("[error] $it") }
            r.errorDetails?.let { d ->
                d.violations?.let { line("[error] Forbidden libs: ${it.joinToString(", ")}") }
                d.allowedPackages?.let { line("[error] Allowed packages: ${it.joinToString(", ")}") }
                d.allowedFrameworks?.let { line("[error] Allowed frameworks: ${it.joinToString(", ")}") }
                d.framework?.let { line("[error] Framework: $it") }
            }
        }

        r.buildOutput?.let { out ->
            section("Build Output")
            out.lines().forEach { line("  $it") }
        }

        r.correctness?.forEach { logScenario(logs, "Correctness", it) }
        r.performance?.forEach { logScenario(logs, "Performance", it) }
        if (r.performanceSkipped == true) {
            section("Performance SKIPPED")
            line("  Reason: ${r.performanceSkipReason ?: "unknown"}")
        }

        r.threadCounts?.let { tc ->
            tc.correctness?.let { line("[threads] Correctness runs: ${it.joinToString(", ")}") }
            tc.performance?.let { line("[threads] Performance runs: ${it.joinToString(", ")}") }
        }

        r.testsDiscovered?.let { td ->
            val parts = listOfNotNull(
                td.correctnessScenarios?.let { "correctness: $it scenarios (${td.correctnessTests ?: "?"} tests)" },
                td.performanceScenarios?.let { "performance: $it scenarios (${td.performanceTests ?: "?"} tests)" },
            )
            if (parts.isNotEmpty()) line("[discovered] ${parts.joinToString(", ")}")
            td.pluginsLoaded?.let { plugins ->
                line("[plugins] Loaded: ${plugins.joinToString(", ") { "${it.name ?: "?"} (${it.status ?: "?"})" }}")
            }
        }

        r.summary?.let { sum ->
            listOfNotNull(
                sum.correctness?.let { "Correctness" to it },
                sum.performance?.let { "Performance" to it },
            ).forEach { (mode, s) ->
                section("Summary: $mode")
                line("  Tests: ${s.passed ?: 0}/${s.totalTests ?: 0} passed, ${s.failed ?: 0} failed")
                val categories = listOfNotNull(
                    s.failedByTimeout?.takeIf { it > 0 }?.let { "$it timeout" },
                    s.failedByOom?.takeIf { it > 0 }?.let { "$it OOM" },
                    s.failedByCrash?.takeIf { it > 0 }?.let { "$it crash" },
                    s.failedByCorrectness?.takeIf { it > 0 }?.let { "$it incorrect" },
                )
                if (categories.isNotEmpty()) line("  Failure breakdown: ${categories.joinToString(", ")}")
                s.maxTimeMs?.let { line("  Max time: %.0fms".format(it)) }
                s.maxRssKb?.let { line("  Peak RSS: ${it / 1024}MB") }
                s.maxCgMemPeakKb?.let { line("  Peak cgroup memory: ${it / 1024}MB") }
                s.totalCpuTimeSec?.let { line("  Total CPU time: %.2fs".format(it)) }
                s.scalability?.let { pts ->
                    line("  Scalability:")
                    line("    Threads | Time      | Speedup | Efficiency | CPU time  | Memory")
                    line("    --------|-----------|---------|------------|-----------|-------")
                    pts.forEach { p ->
                        line("    %7d | %7.0fms | %5.2fx  | %8.0f%%  | %7.2fs  | %dMB".format(
                            p.threads ?: 0, p.totalTimeMs ?: 0.0, p.speedup ?: 0.0,
                            (p.efficiency ?: 0.0) * 100, p.totalCpuTimeSec ?: 0.0,
                            (p.maxRssKb ?: 0) / 1024,
                        ))
                    }
                }
            }
        }

        line("")
        line("[parallel] Done.")
    }

    private fun logScenario(logs: MutableList<String>, label: String, scenario: ScenarioResult) {
        logs.add("")
        logs.add("--- $label: ${scenario.name} ---")
        scenario.metrics?.let { m ->
            logs.add("  T1=%.1fms  Tp=%.1fms  Speedup=%.2fx  Efficiency=%.0f%%".format(
                m.t1Ms, m.tpMs, m.speedup, m.efficiency * 100))
        }
        scenario.tests.forEach { test ->
            test.runs.forEach { run ->
                val icon = if (run.passed) "✓" else "✗"
                val parts = mutableListOf<String>()
                run.stats?.let { parts.add("%.1fms".format(it.timeMs)) }
                run.processStats?.maxRssKb?.let { parts.add("${it / 1024}MB") }
                run.stats?.speedup?.let { parts.add("speedup=%.2fx".format(it)) }
                run.stats?.efficiency?.let { parts.add("eff=%.0f%%".format(it * 100)) }
                run.stats?.computeEfficiency?.let { parts.add("CE=%.0f%%".format(it * 100)) }
                run.stats?.loadBalanceRatio?.let { parts.add("LB=%.2f".format(it)) }
                val detail = if (parts.isNotEmpty()) " [${parts.joinToString(", ")}]" else ""
                logs.add("  $icon ${test.name} (${run.threads}T)$detail")
                if (!run.passed) logFailedRun(logs, run)
            }
        }
    }

    private fun logFailedRun(logs: MutableList<String>, run: TestRun) {
        run.message?.let { logs.add("       Message: $it") }
        run.processStats?.let { ps ->
            if (ps.timedOut == true) logs.add("       ⚠ TIMED OUT (wall=%.1fs, cpu=%.1fs)".format(
                ps.wallTimeSec ?: 0.0, ps.cpuTimeSec ?: 0.0))
            if (ps.oomKilled == true) logs.add("       ⚠ OOM KILLED (peak=${ps.cgMemPeakKb?.let { "${it / 1024}MB" } ?: "?"})")
            if (ps.exitCode != null && ps.exitCode != 0 && ps.exitCode != -1)
                logs.add("       Exit code: ${ps.exitCode}")
        }
        run.stderrOutput?.takeIf { it.isNotBlank() }?.let { stderr ->
            logs.add("       Stderr (first 20 lines):")
            stderr.lines().take(20).forEach { logs.add("         $it") }
        }
        run.parallelStats?.let { ps ->
            val counters = listOfNotNull(
                ps.parallelRegions?.takeIf { it > 0 }?.let { "parallel=$it" },
                ps.forLoops?.takeIf { it > 0 }?.let { "for=$it" },
                ps.barriers?.takeIf { it > 0 }?.let { "barrier=$it" },
                ps.criticals?.takeIf { it > 0 }?.let { "critical=$it" },
                ps.atomics?.takeIf { it > 0 }?.let { "atomic=$it" },
                ps.tasksCreated?.takeIf { it > 0 }?.let { "tasks=$it" },
                ps.taskWaits?.takeIf { it > 0 }?.let { "taskWaits=$it" },
                ps.taskGroups?.takeIf { it > 0 }?.let { "taskGroups=$it" },
                ps.singleRegions?.takeIf { it > 0 }?.let { "single=$it" },
                ps.sections?.takeIf { it > 0 }?.let { "sections=$it" },
                ps.ordered?.takeIf { it > 0 }?.let { "ordered=$it" },
                ps.masters?.takeIf { it > 0 }?.let { "masters=$it" },
                ps.simdConstructs?.takeIf { it > 0 }?.let { "simd=$it" },
                ps.flushes?.takeIf { it > 0 }?.let { "flushes=$it" },
                ps.cancels?.takeIf { it > 0 }?.let { "cancels=$it" },
                ps.taskYields?.takeIf { it > 0 }?.let { "taskYields=$it" },
                ps.maxThreadsUsed?.let { "maxThreads=$it" },
            )
            if (counters.isNotEmpty()) logs.add("       OMP: ${counters.joinToString(", ")}")
        }
    }

    // --- Helpers ---------------------------------------------------------

    private fun mapResultStatus(status: String) = when (status) {
        "completed" -> SubmissionStatus.COMPLETED
        "failed" -> SubmissionStatus.FAILED
        "cancelled" -> SubmissionStatus.REJECTED
        else -> SubmissionStatus.ERROR
    }

    private fun mapJobStatus(status: String) = when (status) {
        "completed" -> JobStatus.SUCCESS
        "failed" -> JobStatus.FAILED
        "cancelled" -> JobStatus.INTERRUPTED
        else -> JobStatus.ERROR
    }

    private fun buildResultSummary(result: TaskResult) = buildString {
        append("Status: ${result.status}")
        result.durationMs?.let { append(", Duration: ${it}ms") }
        result.error?.let { append(", Error: $it") }
    }.take(4000)

    companion object {
        private val LOG = LoggerFactory.getLogger(PipelineServiceImpl::class.java)

        private val TERMINAL_STATUSES = setOf(
            SubmissionStatus.COMPLETED, SubmissionStatus.FAILED,
            SubmissionStatus.ERROR, SubmissionStatus.REJECTED,
        )

        private val STEP_HINTS = mapOf(
            "resolveTests" to "Could not clone/fetch the test repository. Check git URL, branch, and access token.",
            "resolveSolution" to "Could not clone/fetch the solution repository. Check git URL, branch, and access token.",
            "parseConfig" to "Failed to parse assignment config.json on the engine. This is likely an instructor issue.",
            "detectFramework" to "Engine could not detect a supported test framework for the assignment.",
            "validation" to "Student solution uses forbidden libraries/dependencies.",
            "buildRunner" to "Student code failed to compile. See build output below.",
            "buildPlugins" to "Test plugins failed to compile. This is likely an instructor issue.",
            "loadPlugins" to "Engine failed to load test plugins (DLL load or scenario JSON error).",
            "runCorrectness" to "Some correctness tests failed. Performance tests were skipped.",
            "runPerformance" to "Some performance tests failed.",
        )
    }
}
