package com.nelos.parallel.pipeline.runner.service.impl

import com.nelos.parallel.commons.adapter.enums.SourceType
import com.nelos.parallel.commons.adapter.vo.request.SourceDescriptor
import com.nelos.parallel.commons.adapter.vo.request.TaskSubmission
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.pipeline.runner.entity.RunnerConfig
import com.nelos.parallel.pipeline.runner.exception.NoRunnerAvailableException
import com.nelos.parallel.pipeline.runner.exception.RunnerInfraException
import com.nelos.parallel.pipeline.runner.service.RunHandle
import com.nelos.parallel.pipeline.runner.service.RunnerConfigService
import com.nelos.parallel.pipeline.runner.service.RunnerContext
import com.nelos.parallel.pipeline.runner.service.RunnerType
import com.nelos.parallel.pipeline.runner.service.TaskRunner
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class RunnerManagerImplTest {

    private fun ctx() = RunnerContext(
        submissionId = 1L,
        task = TaskSubmission(
            jobId = "1",
            testId = "t",
            solutionSourceType = SourceType.LOCAL,
            solutionSource = SourceDescriptor.LocalSource(path = "/x"),
            testSourceType = SourceType.LOCAL,
            testSource = SourceDescriptor.LocalSource(path = "/y"),
        ),
        onResult = { _: TaskResult -> },
    )

    /** Builds a RunnerConfigService mock that returns the given entries from findAll(). */
    private fun configService(vararg entries: Pair<String, Pair<Int, Boolean>>): RunnerConfigService {
        val rows = entries.map { (n, cfg) ->
            RunnerConfig().apply {
                name = n
                priority = cfg.first
                enabled = cfg.second
            }
        }
        return mock { on { findAll() } doReturn rows }
    }

    private fun runner(
        name: String,
        type: RunnerType = RunnerType.LOCAL,
        available: Boolean = true,
        outcome: (RunnerContext) -> RunHandle? = { handle(name) },
    ): TaskRunner = object : TaskRunner {
        override val type = type
        override val name = name
        override fun isAvailable() = available
        override fun tryRun(ctx: RunnerContext): RunHandle? = outcome(ctx)
    }

    private fun handle(runnerName: String): RunHandle = object : RunHandle {
        override val runnerName = runnerName
        override val jobId = "job-$runnerName"
        override fun cancel() = Unit
    }

    @Test
    fun `dispatch picks the only enabled runner`() {
        val mgr = RunnerManagerImpl(
            runners = listOf(runner("local")),
            runnerConfigService = configService("local" to (1 to true)),
        )
        val h = mgr.dispatch(ctx())
        assertEquals("local", h.runnerName)
    }

    @Test
    fun `dispatch respects priority - higher wins`() {
        val mgr = RunnerManagerImpl(
            runners = listOf(runner("local"), runner("docker")),
            runnerConfigService = configService("local" to (5 to true), "docker" to (1 to true)),
        )
        // Higher value = higher priority -> local (5) beats docker (1).
        assertEquals("local", mgr.dispatch(ctx()).runnerName)
    }

    @Test
    fun `dispatch treats missing config as enabled by default with priority 0`() {
        // Two beans, only one has an explicit row. The runner without a row
        // should still participate (enabled=true default) but lose on priority
        // to one with a higher explicit value.
        val mgr = RunnerManagerImpl(
            runners = listOf(runner("local"), runner("docker")),
            runnerConfigService = configService("local" to (5 to true)),
        )
        assertEquals("local", mgr.dispatch(ctx()).runnerName)
    }

    @Test
    fun `dispatch picks defaulted runner when explicit one is disabled`() {
        val mgr = RunnerManagerImpl(
            runners = listOf(runner("local"), runner("docker")),
            runnerConfigService = configService("local" to (5 to false)),
        )
        assertEquals("docker", mgr.dispatch(ctx()).runnerName)
    }

    @Test
    fun `dispatch falls over on RunnerInfraException`() {
        // local has higher priority than docker but throws on tryRun -> docker takes over.
        val mgr = RunnerManagerImpl(
            runners = listOf(
                runner("local") { throw RunnerInfraException("local", "binary missing") },
                runner("docker"),
            ),
            runnerConfigService = configService("local" to (2 to true), "docker" to (1 to true)),
        )
        assertEquals("docker", mgr.dispatch(ctx()).runnerName)
    }

    @Test
    fun `dispatch falls over on null tryRun`() {
        val mgr = RunnerManagerImpl(
            runners = listOf(
                runner("local") { null },
                runner("docker"),
            ),
            runnerConfigService = configService("local" to (2 to true), "docker" to (1 to true)),
        )
        assertEquals("docker", mgr.dispatch(ctx()).runnerName)
    }

    @Test
    fun `dispatch skips runner with isAvailable=false`() {
        val mgr = RunnerManagerImpl(
            runners = listOf(
                runner("local", available = false),
                runner("docker"),
            ),
            runnerConfigService = configService("local" to (2 to true), "docker" to (1 to true)),
        )
        assertEquals("docker", mgr.dispatch(ctx()).runnerName)
    }

    @Test
    fun `dispatch with empty config dispatches via default-enabled beans`() {
        // No DB rows -> every bean is enabled by default with priority 0.
        // Single bean, so it just wins.
        val mgr = RunnerManagerImpl(
            runners = listOf(runner("local")),
            runnerConfigService = configService(),
        )
        assertEquals("local", mgr.dispatch(ctx()).runnerName)
    }

    @Test
    fun `dispatch throws when every bean is explicitly disabled`() {
        val mgr = RunnerManagerImpl(
            runners = listOf(runner("local"), runner("docker")),
            runnerConfigService = configService(
                "local" to (1 to false),
                "docker" to (2 to false),
            ),
        )
        assertThrows<NoRunnerAvailableException> { mgr.dispatch(ctx()) }
    }

    @Test
    fun `dispatch throws when all runners decline`() {
        // local has higher priority than docker; both decline.
        val mgr = RunnerManagerImpl(
            runners = listOf(
                runner("local") { null },
                runner("docker") { throw RunnerInfraException("docker", "daemon down") },
            ),
            runnerConfigService = configService("local" to (2 to true), "docker" to (1 to true)),
        )
        val ex = assertThrows<NoRunnerAvailableException> { mgr.dispatch(ctx()) }
        assertEquals(2, ex.attempts.size)
        assertEquals(listOf("local", "docker"), ex.attempts.map { it.runnerName })
    }

    @Test
    fun `bean missing from build is silently skipped`() {
        // config has both, but only docker is registered as a bean
        val mgr = RunnerManagerImpl(
            runners = listOf(runner("docker")),
            runnerConfigService = configService("local" to (5 to true), "docker" to (1 to true)),
        )
        assertEquals("docker", mgr.dispatch(ctx()).runnerName)
    }

    @Test
    fun `equal priority round-robins across calls`() {
        val mgr = RunnerManagerImpl(
            runners = listOf(runner("a"), runner("b"), runner("c")),
            runnerConfigService = configService("a" to (1 to true), "b" to (1 to true), "c" to (1 to true)),
        )
        // first dispatch: counter=0, start=0, so order = a,b,c -> picks a
        // second: counter=1, start=1 -> b,c,a -> picks b
        // third: counter=2, start=2 -> c,a,b -> picks c
        // fourth: counter=3, start=0 -> a,b,c -> picks a
        assertEquals("a", mgr.dispatch(ctx()).runnerName)
        assertEquals("b", mgr.dispatch(ctx()).runnerName)
        assertEquals("c", mgr.dispatch(ctx()).runnerName)
        assertEquals("a", mgr.dispatch(ctx()).runnerName)
    }

    @Test
    fun `equal priority round-robin still tries the next on decline`() {
        // a always declines, b accepts. RR should land on a first, then fall over to b.
        val mgr = RunnerManagerImpl(
            runners = listOf(
                runner("a") { null },
                runner("b"),
            ),
            runnerConfigService = configService("a" to (1 to true), "b" to (1 to true)),
        )
        assertEquals("b", mgr.dispatch(ctx()).runnerName)
        // RR advances even when the chosen runner declined; next round starts with b.
        assertEquals("b", mgr.dispatch(ctx()).runnerName)
    }

    @Test
    fun `duplicate runner names rejected at construction`() {
        assertThrows<IllegalArgumentException> {
            RunnerManagerImpl(
                runners = listOf(runner("local"), runner("local", type = RunnerType.DOCKER)),
                runnerConfigService = configService(),
            )
        }
    }

    @Test
    fun `listRunners returns disabled entries when bean exists but config disabled`() {
        val mgr = RunnerManagerImpl(
            runners = listOf(runner("local", type = RunnerType.LOCAL), runner("docker", type = RunnerType.DOCKER)),
            runnerConfigService = configService("local" to (5 to false), "docker" to (1 to true)),
        )
        val snapshot = mgr.listRunners()
        assertEquals(2, snapshot.size)
        // enabled first (docker), then disabled (local)
        assertEquals("docker", snapshot[0].name)
        assertTrue(snapshot[0].enabled)
        assertEquals("local", snapshot[1].name)
        assertFalse(snapshot[1].enabled)
    }

    @Test
    fun `listRunners shows default-enabled for beans without a row`() {
        val mgr = RunnerManagerImpl(
            runners = listOf(runner("local"), runner("docker")),
            runnerConfigService = configService(),
        )
        val snapshot = mgr.listRunners()
        assertEquals(2, snapshot.size)
        assertTrue(snapshot.all { it.enabled })
        assertTrue(snapshot.all { it.priority == 0 })
    }
}
