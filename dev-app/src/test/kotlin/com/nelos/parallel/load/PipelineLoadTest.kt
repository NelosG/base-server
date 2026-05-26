package com.nelos.parallel.load

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.auth.entity.ApiKey
import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.service.ApiKeyService
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.commons.Application
import com.nelos.parallel.gitlab.client.GitLabApiClient
import com.nelos.parallel.gitlab.entity.GitlabUser
import com.nelos.parallel.gitlab.service.GitlabUserService
import com.nelos.parallel.pipeline.data.entity.Assignment
import com.nelos.parallel.pipeline.data.service.AssignmentService
import com.nelos.parallel.pipeline.runner.entity.RunnerConfig
import com.nelos.parallel.pipeline.runner.service.RunnerConfigService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

/**
 * Single-scenario load test: open-loop submit -> engine (5s stub) -> callback ->
 * persist. Drives an adaptive RPS ladder against the orchestrator and emits a
 * markdown report with the operating point and ceiling derived from declared
 * SLOs. Engine is stubbed by LoadTestTaskRunner with unlimited in-flight.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [Application::class])
@ActiveProfiles("loadtest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
@Import(PipelineLoadTestConfig::class)
class PipelineLoadTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var rest: TestRestTemplate
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tracker: InflightTracker

    @Autowired private lateinit var assignmentService: AssignmentService
    @Autowired private lateinit var apiKeyService: ApiKeyService
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var gitlabUserService: GitlabUserService
    @Autowired private lateinit var runnerConfigService: RunnerConfigService
    @Autowired private lateinit var jdbc: org.springframework.jdbc.core.JdbcTemplate

    // GitLab client isn't reachable in the load test; mocked to satisfy autowiring.
    @MockBean private lateinit var gitLabApiClient: GitLabApiClient

    @Value("\${loadtest.engine.task-duration-ms}") private var taskDurationMs: Long = 0
    @Value("\${loadtest.engine.callback-pool-size}") private var callbackPoolSize: Int = 0

    @Value("\${loadtest.combined.discovery.rps-levels}") private lateinit var discoveryRpsLevels: String
    @Value("\${loadtest.combined.discovery.step-sec}") private var discoveryStepSec: Int = 0

    @Value("\${loadtest.combined.steady.start-rps}") private var steadyStartRps: Int = 0
    @Value("\${loadtest.combined.steady.max-rps}") private var steadyMaxRps: Int = 0
    @Value("\${loadtest.combined.steady.step-multiplier}") private var steadyStepMultiplier: Double = 0.0
    @Value("\${loadtest.combined.steady.refine-rounds}") private var steadyRefineRounds: Int = 0
    @Value("\${loadtest.combined.steady.duration-sec}") private var steadyDurationSec: Int = 0
    @Value("\${loadtest.combined.steady.cooldown-sec}") private var steadyCooldownSec: Int = 0

    @Value("\${loadtest.client.submit-pool-size}") private var submitPoolSize: Int = 0

    @Value("\${loadtest.slo.submit-p95-ms}") private var sloSubmitP95Ms: Double = 0.0
    @Value("\${loadtest.slo.submit-p99-ms}") private var sloSubmitP99Ms: Double = 0.0
    @Value("\${loadtest.slo.e2e-p95-ms}") private var sloE2eP95Ms: Double = 0.0
    @Value("\${loadtest.slo.error-rate-pct}") private var sloErrorRatePct: Double = 0.0

    @Value("\${loadtest.drain-timeout-sec}") private var drainTimeoutSec: Int = 0
    @Value("\${loadtest.fixture.students}") private var studentCount: Int = 0
    @Value("\${loadtest.fixture.api-key}") private lateinit var apiKey: String
    @Value("\${server.tomcat.threads.max}") private var tomcatThreadsMax: Int = 0
    @Value("\${spring.datasource.hikari.maximum-pool-size}") private var hikariPoolSize: Int = 0

    private lateinit var assignment: Assignment
    private val studentLogins = mutableListOf<String>()
    private val mrIidCounter = AtomicLong(1000)
    private val discoverySteps = mutableListOf<LoadReportWriter.StepResult>()
    private val steadyStateSteps = mutableListOf<LoadReportWriter.StepResult>()

    @BeforeAll
    fun seed() {
        // Clean any state left from prior runs (defensive against testcontainer reuse).
        jdbc.execute("""
            TRUNCATE TABLE
                prl_submission_log,
                prl_submission_result,
                prl_submission,
                prl_job,
                prl_runner_config,
                prl_assignment,
                prl_gitlab_user,
                prl_api_key,
                prl_user
            RESTART IDENTITY CASCADE
        """.trimIndent())

        assignment = assignmentService.save(Assignment().apply {
            code = "loadtest-lab"
            name = "Load Test Assignment"
            gitlabProjectPath = "loadtest/lab1"
            testRepoUrl = "http://fake/tests.git"
            testRepoBranch = "main"
            threads = 4
            memoryLimitMb = 512L
            wallTimeSec = 30
            cpuTimeSec = 30
            active = true
        })

        apiKeyService.save(ApiKey().apply {
            keyHash = sha256(apiKey)
            keyPrefix = apiKey.take(8)
            name = "loadtest"
            active = true
        })

        repeat(studentCount) { i ->
            val login = "loadstudent-%03d".format(i + 1)
            // We never log students in - encryptedPassword is intentionally a placeholder.
            val user = userService.save(User().apply {
                this.login = login
                encryptedPassword = "x"
                type = UserType.STUDENT
            })
            gitlabUserService.save(GitlabUser().apply {
                userId = user.id
                gitLabName = login
            })
            studentLogins += login
        }

        // Priority above default so the loadtest runner wins dispatch ordering.
        runnerConfigService.save(RunnerConfig().apply {
            name = "loadtest"
            enabled = true
            priority = 10
            settings = objectMapper.createObjectNode()
        })
    }

    @Test
    fun combinedCapacityTest() {
        val runner = scenarioRunner()
        val slo = currentSlo()

        // Phase A: discovery scan at predefined RPS levels (informational, fixed list).
        for (rps in parseRpsLevels(discoveryRpsLevels)) {
            val step = runStep(runner, "discovery", rps, discoveryStepSec)
            discoverySteps += step
            Thread.sleep(2000)
        }

        // Phase B: adaptive steady-state ladder.
        // Walk RPS upward with `step-multiplier` until first SLO breach.
        var rps = steadyStartRps
        var lastPassRps: Int? = null
        var firstFailRps: Int? = null
        while (rps <= steadyMaxRps) {
            val step = runStep(runner, "steady", rps, steadyDurationSec)
            steadyStateSteps += step
            Thread.sleep(steadyCooldownSec * 1000L)
            if (slosMet(step, slo)) {
                lastPassRps = rps
                val next = (rps * steadyStepMultiplier).toInt().coerceAtLeast(rps + 1)
                if (next > steadyMaxRps) {
                    LOG_TEST.info("steady-state reached max-rps {} without breaching SLO; stopping", steadyMaxRps)
                    break
                }
                rps = next
            } else {
                firstFailRps = rps
                break
            }
        }

        // Refinement: narrow the ceiling by running midpoint(s) between last-pass
        // and first-fail. Skipped if either bound is missing.
        val passBound = lastPassRps
        val failBound = firstFailRps
        if (passBound != null && failBound != null && steadyRefineRounds > 0) {
            var low: Int = passBound
            var high: Int = failBound
            repeat(steadyRefineRounds) {
                val mid = (low + high) / 2
                if (mid <= low || mid >= high) return@repeat
                val step = runStep(runner, "refine", mid, steadyDurationSec)
                steadyStateSteps += step
                Thread.sleep(steadyCooldownSec * 1000L)
                if (slosMet(step, slo)) low = mid else high = mid
            }
        }
    }

    @AfterAll
    fun writeReport() {
        val target = Path.of("..", "docs", "load-test", "load-report.md").toAbsolutePath().normalize()
        java.nio.file.Files.createDirectories(target.parent)
        val env = LoadReportWriter.Env(
            cpuThreads = Runtime.getRuntime().availableProcessors(),
            heapMaxMb = Runtime.getRuntime().maxMemory() / 1_000_000,
            tomcatThreadsMax = tomcatThreadsMax,
            hikariPoolSize = hikariPoolSize,
            taskDurationMs = taskDurationMs,
            callbackPoolSize = callbackPoolSize,
        )
        LoadReportWriter(target).write(env, LoadReportWriter.CombinedReport(
            discovery = discoverySteps,
            steadyState = steadyStateSteps,
            slo = currentSlo(),
        ))
        println("[" + LocalDateTime.now() + "] Load report written to: $target")
    }

    private fun currentSlo() = LoadReportWriter.Slo(
        submitP95Ms = sloSubmitP95Ms,
        submitP99Ms = sloSubmitP99Ms,
        e2eP95Ms = sloE2eP95Ms,
        errorRatePct = sloErrorRatePct,
    )

    private fun parseRpsLevels(csv: String): List<Int> =
        csv.split(',').map { it.trim().toInt() }

    private fun runStep(
        runner: ScenarioRunner,
        phase: String,
        rps: Int,
        durationSec: Int,
    ): LoadReportWriter.StepResult {
        val stats = LoadStats("$phase@$rps")
        tracker.setActiveStats(stats)
        runner.runOpenLoop(stats, rps, durationSec)
        drainTracker(stats, taskDurationMs / 1000 + 30)
        tracker.setActiveStats(null)
        tracker.resetForScenario()
        return LoadReportWriter.StepResult(rps = rps, durationSec = durationSec, snapshot = stats.snapshot(), phase = phase)
    }

    private fun drainTracker(stats: LoadStats, drainSec: Long) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(drainSec)
        while (tracker.inflightCount() > 0 && System.nanoTime() < deadline) Thread.sleep(50)
        if (tracker.inflightCount() > 0) stats.recordDrainTimeout(tracker.inflightCount().toLong())
    }

    private fun slosMet(step: LoadReportWriter.StepResult, slo: LoadReportWriter.Slo): Boolean {
        val s = step.snapshot
        val errPct = if (s.submitTotal == 0L) 100.0 else s.submitErrors * 100.0 / s.submitTotal
        if (errPct >= slo.errorRatePct) return false
        if (s.submitLatency.p95Ms > slo.submitP95Ms) return false
        if (s.submitLatency.p99Ms > slo.submitP99Ms) return false
        if (s.e2eLatency.p95Ms > slo.e2eP95Ms && s.e2eLatency.count > 0) return false
        return true
    }

    private fun scenarioRunner() = ScenarioRunner(
        baseUrl = "http://localhost:$port",
        apiKey = apiKey,
        rest = rest,
        gitlabProjectPath = assignment.gitlabProjectPath!!,
        studentLogins = studentLogins,
        mrIidCounter = mrIidCounter,
        objectMapper = objectMapper,
        tracker = tracker,
        submitPoolSize = submitPoolSize,
    )

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val LOG_TEST = org.slf4j.LoggerFactory.getLogger(PipelineLoadTest::class.java)

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("loadtest")
            withUsername("loadtest")
            withPassword("loadtest")
            // Reuse is intentionally OFF: it carries data across runs and causes
            // PK collisions on the seed inserts. ~5-10s startup overhead is fine.
            // Eagerly start so DynamicPropertySource can resolve jdbcUrl during
            // Spring context bootstrap (which happens before the Testcontainers
            // BeforeAll callback would otherwise start the container).
            start()
        }

        @DynamicPropertySource
        @JvmStatic
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.datasource.driver-class-name") { postgres.driverClassName }
        }
    }
}
