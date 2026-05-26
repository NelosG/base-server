package com.nelos.parallel.runners.docker

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.git.service.GitReacher
import com.nelos.parallel.pipeline.runner.exception.RunnerInfraException
import com.nelos.parallel.pipeline.runner.service.RunHandle
import com.nelos.parallel.pipeline.runner.service.RunnerContext
import com.nelos.parallel.pipeline.runner.service.RunnerSettingsProvider
import com.nelos.parallel.pipeline.runner.service.RunnerType
import com.nelos.parallel.pipeline.runner.service.TaskRunner
import com.nelos.parallel.runners.commons.CliWorkspace
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Spawns the c-tests-runner Docker image with `cli` entrypoint once per task.
 * Pre-clones sources via [CliWorkspace] and bind-mounts the workspace into
 * the container - the container's cli sees `/work/tests`, `/work/solution`,
 * writes `/work/result.json` back onto the host.
 *
 * Settings (image name, docker binary, extra args, ...) live in
 * `prl_runner_config.settings` and are edited via the /runners admin page.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.dockerRunner")
class DockerCliTaskRunner(
    private val settingsProvider: RunnerSettingsProvider,
    private val gitReacher: GitReacher,
    private val objectMapper: ObjectMapper,
) : TaskRunner {

    override val type: RunnerType = RunnerType.DOCKER
    override val name: String = "docker"

    @Volatile
    private var executor: ExecutorService? = null

    // jobId -> container name (used to docker kill on cancel/shutdown)
    private val runningContainers = ConcurrentHashMap<String, String>()

    private fun settings(): DockerRunnerSettings =
        settingsProvider.get(name, DockerRunnerSettings::class.java) { DockerRunnerSettings() }

    private fun executor(): ExecutorService {
        return executor ?: synchronized(this) {
            executor ?: Executors.newFixedThreadPool(settings().maxConcurrent.coerceAtLeast(1)) { r ->
                Thread(r, "prl-docker-runner").apply { isDaemon = true }
            }.also { executor = it }
        }
    }

    override fun isAvailable(): Boolean = settings().imageName.isNotBlank()

    override fun tryRun(ctx: RunnerContext): RunHandle? {
        val jobId = ctx.task.jobId
            ?: throw RunnerInfraException(name, "task has no jobId")
        val s = settings()
        if (s.imageName.isBlank()) return null

        val workspace = CliWorkspace(Path.of(s.workdirBase, jobId), gitReacher)
        try {
            workspace.prepare(ctx.task)
        } catch (e: Exception) {
            workspace.cleanup()
            throw RunnerInfraException(name, "workspace prepare failed: ${e.message}", e)
        }
        val containerName = "prl-cli-${jobId}-${UUID.randomUUID().toString().take(8)}"
        val command = buildDockerCommand(s, ctx, workspace, containerName)
        LOG.info("submission {}: spawning container '{}': {}",
            ctx.submissionId, containerName, command.joinToString(" "))

        executor().submit { runJob(ctx, jobId, command, workspace, containerName, s.dockerBinary, s.shutdownGraceSec) }
        return DockerRunHandle(jobId, containerName, runningContainers, s.dockerBinary, s.shutdownGraceSec)
    }

    private fun runJob(
        ctx: RunnerContext,
        jobId: String,
        command: List<String>,
        workspace: CliWorkspace,
        containerName: String,
        dockerBinary: String,
        graceSec: Int,
    ) {
        val stdoutFile = workspace.testsDir.parent.resolve("stdout.log")
        val stderrFile = workspace.testsDir.parent.resolve("stderr.log")
        val startedAt = Instant.now()
        val process = try {
            ProcessBuilder(command)
                .directory(workspace.testsDir.parent.toFile())
                .redirectOutput(stdoutFile.toFile())
                .redirectError(stderrFile.toFile())
                .start()
        } catch (e: Exception) {
            LOG.error("submission {}: docker spawn failed", ctx.submissionId, e)
            ctx.onResult(syntheticError(jobId, "docker spawn failed: ${e.message}"))
            workspace.cleanup()
            return
        }
        runningContainers[jobId] = containerName
        try {
            val exitCode = process.waitFor()
            val durationMs = Duration.between(startedAt, Instant.now()).toMillis().toDouble()
            val result = parseResultOrSynthesize(jobId, workspace.resultFile, exitCode, stderrFile, durationMs)
            ctx.onResult(result)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            killContainer(dockerBinary, containerName, graceSec)
            process.destroyForcibly()
            ctx.onResult(syntheticError(jobId, "docker runner interrupted"))
        } finally {
            runningContainers.remove(jobId)
            workspace.cleanup()
        }
    }

    private fun buildDockerCommand(
        s: DockerRunnerSettings,
        ctx: RunnerContext,
        workspace: CliWorkspace,
        containerName: String,
    ): List<String> {
        val hostMount = workspace.testsDir.parent.toAbsolutePath().toString()
        val cmd = mutableListOf(s.dockerBinary, "run", "--rm",
            "--name", containerName,
            "-v", "$hostMount:${s.containerWorkdir}",
        )
        cmd += s.extraArgs
        cmd += s.imageName
        cmd += "cli"
        ctx.task.testId?.let { cmd += listOf("--test-id", it) }
        cmd += listOf("--test-dir", "${s.containerWorkdir}/tests")
        cmd += listOf("--solution", "${s.containerWorkdir}/solution")
        ctx.task.threads?.let { cmd += listOf("--threads", it.toString()) }
        ctx.task.memoryLimitMb?.let { cmd += listOf("--memory-limit", it.toString()) }
        ctx.task.wallTimeSec?.let { cmd += listOf("--wall-time", it.toString()) }
        ctx.task.cpuTimeSec?.let { cmd += listOf("--cpu-time", it.toString()) }
        ctx.task.warmupIterations?.let { cmd += listOf("--warmup", it.toString()) }
        cmd += listOf("--output", "${s.containerWorkdir}/result.json")
        return cmd
    }

    private fun killContainer(dockerBinary: String, name: String, graceSec: Int) {
        try {
            ProcessBuilder(dockerBinary, "kill", name)
                .redirectErrorStream(true).start()
                .waitFor(graceSec.toLong(), TimeUnit.SECONDS)
        } catch (e: Exception) {
            LOG.warn("docker kill {} failed: {}", name, e.message)
        }
    }

    internal fun parseResultOrSynthesize(
        jobId: String,
        resultFile: Path,
        exitCode: Int,
        stderrFile: Path,
        durationMs: Double,
    ): TaskResult {
        if (Files.isRegularFile(resultFile)) {
            val rootNode = try {
                objectMapper.readTree(resultFile.toFile())
            } catch (e: Exception) {
                LOG.error("submission jobId={}: result.json malformed", jobId, e)
                return syntheticError(jobId, "result.json malformed: ${e.message}", durationMs)
            }
            val taskNode = if (rootNode.isArray) rootNode.firstOrNull() ?: return syntheticError(
                jobId, "result.json was an empty array", durationMs
            ) else rootNode
            val parsed = try {
                objectMapper.treeToValue(taskNode, TaskResult::class.java)
            } catch (e: Exception) {
                LOG.error("submission jobId={}: TaskResult deserialization failed", jobId, e)
                return syntheticError(jobId, "TaskResult deserialization failed: ${e.message}", durationMs)
            }
            return if (parsed.jobId.isBlank()) parsed.withJobId(jobId) else parsed
        }
        val errTail = try {
            if (Files.isRegularFile(stderrFile)) Files.readString(stderrFile).takeLast(2000) else ""
        } catch (_: Exception) {
            ""
        }
        val reason = if (exitCode != 0) "docker exited with code $exitCode" else "container exited 0 but wrote no result.json"
        return syntheticError(jobId, "$reason${if (errTail.isNotEmpty()) " - $errTail" else ""}", durationMs)
    }

    private fun syntheticError(jobId: String, message: String, durationMs: Double? = null): TaskResult =
        TaskResult(
            jobId = jobId,
            nodeId = "docker",
            status = "error",
            error = message,
            durationMs = durationMs,
        )

    @PreDestroy
    fun shutdown() {
        val s = settings()
        runningContainers.values.toList().forEach { killContainer(s.dockerBinary, it, s.shutdownGraceSec) }
        executor?.shutdownNow()
    }

    private class DockerRunHandle(
        override val jobId: String,
        private val containerName: String,
        private val runningContainers: ConcurrentHashMap<String, String>,
        private val dockerBinary: String,
        private val graceSec: Int,
    ) : RunHandle {
        override val runnerName: String = "docker"
        override fun cancel() {
            runningContainers[jobId] ?: return
            try {
                ProcessBuilder(dockerBinary, "kill", containerName)
                    .redirectErrorStream(true).start()
                    .waitFor(graceSec.toLong(), TimeUnit.SECONDS)
            } catch (e: Exception) {
                LoggerFactory.getLogger(DockerRunHandle::class.java)
                    .warn("docker kill {} failed: {}", containerName, e.message)
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DockerCliTaskRunner::class.java)
        private fun TaskResult.withJobId(newJobId: String): TaskResult = TaskResult(
            jobId = newJobId, nodeId = this.nodeId, status = this.status, error = this.error,
            durationMs = this.durationMs, timestamp = this.timestamp, mode = this.mode,
            solution = this.solution, buildOutput = this.buildOutput, correctness = this.correctness,
            performance = this.performance, buildInfo = this.buildInfo, threadCounts = this.threadCounts,
            totalTimeMs = this.totalTimeMs, assignmentConfig = this.assignmentConfig, pipeline = this.pipeline,
            effectiveParams = this.effectiveParams, environment = this.environment, failedStep = this.failedStep,
            errorDetails = this.errorDetails, testsDiscovered = this.testsDiscovered, summary = this.summary,
            performanceSkipped = this.performanceSkipped, performanceSkipReason = this.performanceSkipReason,
        )
    }
}
