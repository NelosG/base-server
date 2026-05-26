package com.nelos.parallel.runners.docker

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.git.service.GitReacher
import com.nelos.parallel.pipeline.runner.service.RunnerSettingsProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.nio.file.Files
import java.nio.file.Path

class DockerCliTaskRunnerTest {

    @TempDir
    lateinit var temp: Path

    private val gitReacher: GitReacher = mock()
    private val objectMapper = ObjectMapper()
    private lateinit var runner: DockerCliTaskRunner
    private lateinit var settingsProvider: RunnerSettingsProvider
    private var settings = DockerRunnerSettings()

    @BeforeEach
    fun setUp() {
        settingsProvider = mock {
            on { get(eq("docker"), eq(DockerRunnerSettings::class.java), any()) } doAnswer { settings }
        }
        settings = DockerRunnerSettings(
            dockerBinary = "docker",
            imageName = "",
            workdirBase = temp.resolve("workdir").toString(),
            maxConcurrent = 1,
            shutdownGraceSec = 2,
        )
        runner = DockerCliTaskRunner(settingsProvider, gitReacher, objectMapper)
    }

    @AfterEach
    fun tearDown() {
        runner.shutdown()
    }

    @Test
    fun `isAvailable=false when imageName blank`() {
        assertFalse(runner.isAvailable())
    }

    @Test
    fun `isAvailable=true when imageName set`() {
        settings = DockerRunnerSettings(imageName = "ctr:latest")
        assertTrue(runner.isAvailable())
    }

    @Test
    fun `parseResultOrSynthesize returns parsed TaskResult on success`() {
        val resultFile = temp.resolve("result.json")
        Files.writeString(resultFile, """{"jobId":"engine-1","status":"completed","durationMs":100}""")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "") }
        val parsed = runner.parseResultOrSynthesize("orch-1", resultFile, 0, stderr, 200.0)
        assertEquals("engine-1", parsed.jobId)
        assertEquals("completed", parsed.status)
    }

    @Test
    fun `parseResultOrSynthesize stamps jobId when engine left it blank`() {
        val resultFile = temp.resolve("result.json")
        Files.writeString(resultFile, """{"jobId":"","status":"completed"}""")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "") }
        val parsed = runner.parseResultOrSynthesize("sub-77", resultFile, 0, stderr, 50.0)
        assertEquals("sub-77", parsed.jobId)
    }

    @Test
    fun `parseResultOrSynthesize handles missing result file`() {
        val resultFile = temp.resolve("not-here.json")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "container died\n") }
        val parsed = runner.parseResultOrSynthesize("j1", resultFile, 137, stderr, 10.0)
        assertEquals("error", parsed.status)
        assertTrue(parsed.error?.contains("exited with code 137") == true,
            "expected error to mention exit code, got: ${parsed.error}")
    }

    @Test
    fun `parseResultOrSynthesize handles malformed json`() {
        val resultFile = temp.resolve("bad.json")
        Files.writeString(resultFile, "}}}{{{")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "") }
        val parsed = runner.parseResultOrSynthesize("j2", resultFile, 0, stderr, 5.0)
        assertEquals("error", parsed.status)
        assertTrue(parsed.error?.contains("malformed") == true)
    }

    @Test
    fun `parseResultOrSynthesize unwraps a single-element JSON array`() {
        val resultFile = temp.resolve("array.json")
        Files.writeString(resultFile, """[{"jobId":"j","status":"completed"}]""")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "") }
        val parsed = runner.parseResultOrSynthesize("orch", resultFile, 0, stderr, 0.0)
        assertEquals("j", parsed.jobId)
    }

    @Test
    fun `parseResultOrSynthesize errors on an empty array`() {
        val resultFile = temp.resolve("empty.json")
        Files.writeString(resultFile, "[]")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "") }
        val parsed = runner.parseResultOrSynthesize("j", resultFile, 0, stderr, 0.0)
        assertEquals("error", parsed.status)
        assertTrue(parsed.error?.contains("empty array") == true)
    }

    @Test
    fun `parseResultOrSynthesize reports container exited 0 without result`() {
        val resultFile = temp.resolve("missing.json")
        val stderr = temp.resolve("stderr.log").also { Files.writeString(it, "") }
        val parsed = runner.parseResultOrSynthesize("j", resultFile, 0, stderr, 0.0)
        assertTrue(parsed.error?.contains("exited 0 but wrote no result.json") == true)
    }

    @Test
    fun `parseResultOrSynthesize tolerates an unreadable stderr file`() {
        val resultFile = temp.resolve("nope.json")
        val stderr = temp.resolve("no-stderr.log") // intentionally absent
        val parsed = runner.parseResultOrSynthesize("j", resultFile, 1, stderr, 0.0)
        assertEquals("error", parsed.status)
        assertTrue(parsed.error?.startsWith("docker exited with code 1") == true)
    }

    @Test
    fun `tryRun without a jobId is an infra failure`() {
        settings = DockerRunnerSettings(imageName = "ctr:latest")
        val task = com.nelos.parallel.commons.adapter.vo.request.TaskSubmission(jobId = null)
        val ctx = com.nelos.parallel.pipeline.runner.service.RunnerContext(
            submissionId = 1L, task = task, onResult = { },
        )
        org.junit.jupiter.api.assertThrows<com.nelos.parallel.pipeline.runner.exception.RunnerInfraException> {
            runner.tryRun(ctx)
        }
    }

    @Test
    fun `tryRun returns null when imageName is blank`() {
        val task = com.nelos.parallel.commons.adapter.vo.request.TaskSubmission(jobId = "1")
        val ctx = com.nelos.parallel.pipeline.runner.service.RunnerContext(
            submissionId = 1L, task = task, onResult = { },
        )
        assertNull(runner.tryRun(ctx))
    }

    @Test
    fun `tryRun wraps workspace prepare failure in a RunnerInfraException`() {
        settings = DockerRunnerSettings(
            imageName = "ctr:latest",
            workdirBase = temp.resolve("dwork").toString(),
            maxConcurrent = 1,
            shutdownGraceSec = 2,
        )
        // GIT source with no token forces CliWorkspace.prepare to fail.
        val task = com.nelos.parallel.commons.adapter.vo.request.TaskSubmission(
            jobId = "j1",
            testSourceType = com.nelos.parallel.commons.adapter.enums.SourceType.GIT,
            testSource = com.nelos.parallel.commons.adapter.vo.request.SourceDescriptor.GitSource(
                url = "x", token = null,
            ),
            solutionSourceType = com.nelos.parallel.commons.adapter.enums.SourceType.LOCAL,
            solutionSource = com.nelos.parallel.commons.adapter.vo.request.SourceDescriptor.LocalSource(
                path = temp.toString(),
            ),
        )
        val ctx = com.nelos.parallel.pipeline.runner.service.RunnerContext(
            submissionId = 1L, task = task, onResult = { },
        )
        org.junit.jupiter.api.assertThrows<com.nelos.parallel.pipeline.runner.exception.RunnerInfraException> {
            runner.tryRun(ctx)
        }
    }
}
