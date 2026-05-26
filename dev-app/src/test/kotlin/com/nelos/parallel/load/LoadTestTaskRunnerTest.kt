package com.nelos.parallel.load

import com.nelos.parallel.commons.adapter.enums.SourceType
import com.nelos.parallel.commons.adapter.vo.request.SourceDescriptor
import com.nelos.parallel.commons.adapter.vo.request.TaskSubmission
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.pipeline.runner.service.RunnerContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoadTestTaskRunnerTest {

    private val runner = LoadTestTaskRunner(taskDurationMs = 50, callbackPoolSize = 16)

    @AfterEach
    fun tearDown() = runner.shutdown()

    @Test
    fun `schedules callback after taskDurationMs and reports completed status`() {
        val received = AtomicReference<TaskResult>()
        val latch = CountDownLatch(1)
        val ctx = RunnerContext(
            submissionId = 42L,
            task = task(jobId = "42"),
            onResult = { received.set(it); latch.countDown() },
        )

        val started = System.nanoTime()
        val handle = runner.tryRun(ctx)
        assertNotNull(handle)
        assertEquals("loadtest", handle.runnerName)

        assertTrue(latch.await(2, TimeUnit.SECONDS), "callback never fired")
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMs >= 50, "callback fired too early: $elapsedMs ms")
        assertEquals("completed", received.get().status)
        assertEquals("42", received.get().jobId)
    }

    @Test
    fun `is always available`() {
        assertTrue(runner.isAvailable())
    }

    private fun task(jobId: String) = TaskSubmission(
        jobId = jobId, testId = "loadtest",
        solutionSourceType = SourceType.GIT,
        solutionSource = SourceDescriptor.GitSource(url = "http://example/x.git", branch = "main", token = null),
        testSourceType = SourceType.GIT,
        testSource = SourceDescriptor.GitSource(url = "http://example/t.git", branch = "main", token = null),
        threads = 4,
    )
}
