package com.nelos.parallel.runners.local

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.adapter.enums.SourceType
import com.nelos.parallel.commons.adapter.vo.request.SourceDescriptor
import com.nelos.parallel.commons.adapter.vo.request.TaskSubmission
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.git.service.GitReacher
import com.nelos.parallel.pipeline.runner.exception.RunnerInfraException
import com.nelos.parallel.pipeline.runner.service.RunnerContext
import com.nelos.parallel.pipeline.runner.service.RunnerSettingsProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path

class LocalCliTaskRunnerTest {

    @TempDir
    lateinit var temp: Path

    private val gitReacher: GitReacher = mock()
    private val objectMapper = ObjectMapper()
    private lateinit var runner: LocalCliTaskRunner
    private lateinit var settingsProvider: RunnerSettingsProvider
    private var settings = LocalRunnerSettings()

    @BeforeEach
    fun setUp() {
        // Provider returns the mutable `settings` field so each test can reshape
        // it before exercising the runner.
        settingsProvider = mock {
            on { get(eq("local"), eq(LocalRunnerSettings::class.java), any()) } doAnswer { settings }
        }
        settings = LocalRunnerSettings(
            binaryPath = "",
            workdirBase = temp.resolve("workdir").toString(),
            maxConcurrent = 1,
            shutdownGraceSec = 2,
        )
        runner = LocalCliTaskRunner(settingsProvider, gitReacher, objectMapper)
    }

    @AfterEach
    fun tearDown() {
        runner.shutdown()
    }

    private fun localPathTask(jobId: String = "42"): TaskSubmission {
        val tests = temp.resolve("tests").also { Files.createDirectories(it) }
        val solution = temp.resolve("solution").also { Files.createDirectories(it) }
        return TaskSubmission(
            jobId = jobId,
            testId = "lab1",
            solutionSourceType = SourceType.LOCAL,
            solutionSource = SourceDescriptor.LocalSource(path = solution.toString()),
            testSourceType = SourceType.LOCAL,
            testSource = SourceDescriptor.LocalSource(path = tests.toString()),
        )
    }

    @Test
    fun `isAvailable=false when binary path is blank`() {
        assertFalse(runner.isAvailable())
    }

    @Test
    fun `isAvailable=true when binary path is set`() {
        settings = settings.copyWith(binaryPath = "/anything")
        assertTrue(runner.isAvailable())
    }

    @Test
    fun `tryRun throws RunnerInfraException when binary file does not exist`() {
        settings = settings.copyWith(binaryPath = temp.resolve("nope").toString())
        val task = localPathTask()
        val ctx = RunnerContext(submissionId = 1L, task = task, onResult = { _: TaskResult -> })
        assertThrows<RunnerInfraException> { runner.tryRun(ctx) }
    }

    @Test
    fun `tryRun returns null when binary path blank`() {
        val task = localPathTask()
        val ctx = RunnerContext(submissionId = 1L, task = task, onResult = { _: TaskResult -> })
        assertNull(runner.tryRun(ctx))
    }

    @Test
    fun `parseResultOrSynthesize unwraps single-element JSON array`() {
        val resultFile = temp.resolve("result.json")
        Files.writeString(resultFile, """[{"jobId":"engine-job","status":"completed","durationMs":42}]""")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "") }
        val parsed = runner.parseResultOrSynthesize("orchestrator-7", resultFile, 0, stderr, 100.0)
        assertEquals("engine-job", parsed.jobId)
        assertEquals("completed", parsed.status)
    }

    @Test
    fun `parseResultOrSynthesize stamps orchestrator jobId when engine left it blank`() {
        val resultFile = temp.resolve("result.json")
        Files.writeString(resultFile, """{"jobId":"","status":"completed"}""")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "") }
        val parsed = runner.parseResultOrSynthesize("submission-99", resultFile, 0, stderr, 50.0)
        assertEquals("submission-99", parsed.jobId)
        assertEquals("completed", parsed.status)
    }

    @Test
    fun `parseResultOrSynthesize returns synthetic error when result file is absent`() {
        val resultFile = temp.resolve("does-not-exist.json")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "cli boom\n") }
        val parsed = runner.parseResultOrSynthesize("j1", resultFile, 0, stderr, 10.0)
        assertEquals("error", parsed.status)
        assertTrue(parsed.error?.contains("wrote no result.json") == true)
    }

    @Test
    fun `parseResultOrSynthesize returns synthetic error when JSON is malformed`() {
        val resultFile = temp.resolve("bad.json")
        Files.writeString(resultFile, "not-json{{{")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "") }
        val parsed = runner.parseResultOrSynthesize("j2", resultFile, 0, stderr, 10.0)
        assertEquals("error", parsed.status)
        assertTrue(parsed.error?.contains("malformed") == true)
    }

    @Test
    fun `parseResultOrSynthesize errors on an empty json array`() {
        val resultFile = temp.resolve("empty.json")
        Files.writeString(resultFile, "[]")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "") }
        val parsed = runner.parseResultOrSynthesize("j3", resultFile, 0, stderr, 10.0)
        assertEquals("error", parsed.status)
        assertTrue(parsed.error?.contains("empty array") == true)
    }

    @Test
    fun `parseResultOrSynthesize accepts an object (not array)`() {
        val resultFile = temp.resolve("obj.json")
        Files.writeString(resultFile, """{"jobId":"x","status":"completed"}""")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "") }
        val parsed = runner.parseResultOrSynthesize("orch", resultFile, 0, stderr, 0.0)
        assertEquals("x", parsed.jobId)
        assertEquals("completed", parsed.status)
    }

    @Test
    fun `parseResultOrSynthesize includes exit code and stderr tail when no result file written`() {
        val resultFile = temp.resolve("missing.json")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "segfault at 0xdead\nbye\n") }
        val parsed = runner.parseResultOrSynthesize("j4", resultFile, 139, stderr, 10.0)
        assertTrue(parsed.error?.contains("exited with code 139") == true)
        assertTrue(parsed.error?.contains("segfault") == true)
    }

    @Test
    fun `parseResultOrSynthesize handles unreadable stderr gracefully`() {
        // stderr file doesn't exist - we shouldn't fail the result parsing.
        val resultFile = temp.resolve("missing.json")
        val stderr = temp.resolve("no-stderr.log")
        val parsed = runner.parseResultOrSynthesize("j5", resultFile, 1, stderr, 5.0)
        assertEquals("error", parsed.status)
        assertTrue(parsed.error?.startsWith("cli exited with code 1") == true)
    }

    @Test
    fun `tryRun without a jobId is an infra failure`() {
        settings = settings.copyWith(binaryPath = "/anything")
        val task = TaskSubmission(jobId = null, testId = "lab1")
        val ctx = RunnerContext(submissionId = 1L, task = task, onResult = { _: TaskResult -> })
        assertThrows<RunnerInfraException> { runner.tryRun(ctx) }
    }

    @Test
    fun `tryRun wraps workspace prepare failure in RunnerInfraException`() {
        val binary = temp.resolve("cli-bin")
        Files.writeString(binary, "")
        binary.toFile().setExecutable(true)
        settings = settings.copyWith(binaryPath = binary.toString())
        // GIT source with no token -> CliWorkspace.prepare blows up.
        val task = TaskSubmission(
            jobId = "j1", testId = "lab1",
            testSourceType = SourceType.GIT,
            testSource = SourceDescriptor.GitSource(url = "x", token = null),
            solutionSourceType = SourceType.LOCAL,
            solutionSource = SourceDescriptor.LocalSource(path = temp.toString()),
        )
        val ctx = RunnerContext(submissionId = 1L, task = task, onResult = { _: TaskResult -> })

        assertThrows<RunnerInfraException> { runner.tryRun(ctx) }
    }

    private fun LocalRunnerSettings.copyWith(
        binaryPath: String = this.binaryPath,
        workdirBase: String = this.workdirBase,
        maxConcurrent: Int = this.maxConcurrent,
        shutdownGraceSec: Int = this.shutdownGraceSec,
    ) = LocalRunnerSettings(binaryPath, workdirBase, maxConcurrent, shutdownGraceSec)

    private fun <T> eq(value: T): T = org.mockito.kotlin.eq(value)
}
