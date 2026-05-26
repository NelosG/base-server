package com.nelos.parallel.pipeline.core.service.impl

import com.nelos.parallel.commons.adapter.vo.response.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the data -> text rendering of [SubmissionResultLogFormatter]. Each
 * nested group focuses on one section of the produced log so failures point
 * to a clear region.
 *
 * The formatter passes `Locale.ROOT` to every locale-sensitive `String.format`
 * call (numbers always emit `.` as decimal separator), so these assertions
 * are deterministic across CI hosts.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class SubmissionResultLogFormatterTest {

    private val formatter = SubmissionResultLogFormatter()

    private fun render(result: TaskResult): List<String> =
        mutableListOf<String>().also { formatter.appendResultLogs(it, result) }

    // --- header + top-level info ----------------------------------------

    @Nested
    inner class HeaderAndMetadata {

        @Test
        fun `header shows uppercased status`() {
            val logs = render(taskResult(status = "failed"))

            assertTrue(logs.any { it.contains("Test execution finished: FAILED") })
        }

        @Test
        fun `info lines appear when the corresponding fields are set`() {
            val logs = render(
                taskResult(
                    solution = "/tmp/sln",
                    nodeId = "node-A",
                    durationMs = 1234.0,
                ),
            )

            assertContains(logs, "[info] Solution: /tmp/sln")
            assertContains(logs, "[info] Engine node: node-A")
            assertContains(logs, "[info] Total duration: 1234ms")
        }

        @Test
        fun `environment line is skipped when no parts are present`() {
            val logs = render(
                taskResult(
                    environment = EnvironmentInfo(platform = null, hardwareThreads = null),
                ),
            )

            assertFalse(logs.any { it.startsWith("[env]") })
        }

        @Test
        fun `environment line joins available parts`() {
            val logs = render(
                taskResult(
                    environment = EnvironmentInfo(platform = "linux", hardwareThreads = HW_THREADS),
                ),
            )

            assertContains(logs, "[env] platform=linux, hw_threads=$HW_THREADS")
        }

        @Test
        fun `effective params line drops null sub-fields`() {
            val logs = render(
                taskResult(
                    effectiveParams = EffectiveParams(
                        mode = "stress", threads = THREADS,
                        memoryLimitMb = null, wallTimeSec = WALL_TIME_SEC, cpuTimeSec = null,
                    ),
                ),
            )

            assertContains(
                logs,
                "[params] mode=stress, threads=$THREADS, wall_time=${WALL_TIME_SEC}s",
            )
        }

        @Test
        fun `assignment config drops empty list fields`() {
            val logs = render(
                taskResult(
                    assignmentConfig = AssignmentConfig(
                        name = "lab1", framework = "gtest", mode = "perf",
                        correctnessMode = null,
                        allowedFrameworks = emptyList(),
                        allowedPackages = listOf("std", "omp"),
                    ),
                ),
            )

            val line = logs.single { it.startsWith("[config]") }
            assertContains(line, "name=lab1")
            assertContains(line, "framework=gtest")
            assertContains(line, "mode=perf")
            assertFalse(line.contains("allowedFrameworks"), "empty list must be dropped")
            assertContains(line, "allowedPackages=std/omp")
        }
    }

    // --- pipeline + failure section -------------------------------------

    @Nested
    inner class PipelineSection {

        @Test
        fun `pipeline lists each step with status icon and total`() {
            val logs = render(
                taskResult(
                    pipeline = listOf(
                        pipelineStep("resolveTests", "ok", 10.0),
                        pipelineStep("buildRunner", "ok", 200.0),
                    ),
                    totalTimeMs = 230.0,
                ),
            )

            assertTrue(logs.any { it.contains("--- Pipeline Steps ---") })
            assertTrue(logs.any { it.contains("✓ resolveTests - ok") })
            assertTrue(logs.any { it.contains("✓ buildRunner - ok") })
            assertTrue(logs.any { it.contains("Total: 230ms") })
        }

        @Test
        fun `failing step gets a cross icon`() {
            val logs = render(
                taskResult(
                    pipeline = listOf(pipelineStep("buildRunner", "failed", 80.0)),
                ),
            )

            assertTrue(logs.any { it.contains("✗ buildRunner - failed") })
        }
    }

    @Nested
    inner class FailureSection {

        @Test
        fun `known failed step adds a hint`() {
            val logs = render(
                taskResult(
                    status = "failed",
                    failedStep = "buildRunner",
                    error = "g++: error: no such file",
                ),
            )

            assertTrue(logs.any { it.contains("--- FAILURE at step: buildRunner ---") })
            assertTrue(logs.any { it.startsWith("[hint] Student code failed to compile") })
            assertContains(logs, "[error] g++: error: no such file")
        }

        @Test
        fun `unknown step has no hint but keeps error and details`() {
            val logs = render(
                taskResult(
                    status = "failed",
                    failedStep = "unknownPhase",
                    error = "boom",
                    errorDetails = ErrorDetails(
                        violations = listOf("libfoo", "libbar"),
                        framework = "gtest",
                    ),
                ),
            )

            assertFalse(logs.any { it.startsWith("[hint]") })
            assertContains(logs, "[error] Forbidden libs: libfoo, libbar")
            assertContains(logs, "[error] Framework: gtest")
        }

        @Test
        fun `build plugin load error is surfaced`() {
            val logs = render(
                taskResult(
                    buildInfo = BuildInfo(pluginLoadError = "could not open libplugin.so"),
                ),
            )

            assertContains(logs, "[build] Plugin load error: could not open libplugin.so")
        }

        @Test
        fun `build output is broken into lines under a section header`() {
            val logs = render(taskResult(buildOutput = "warning: foo\nerror: bar"))

            assertTrue(logs.any { it.contains("--- Build Output ---") })
            assertContains(logs, "  warning: foo")
            assertContains(logs, "  error: bar")
        }
    }

    // --- scenarios + failed runs ----------------------------------------

    @Nested
    inner class ScenarioLogging {

        @Test
        fun `correctness scenarios are grouped under their label`() {
            val logs = render(
                taskResult(
                    correctness = listOf(
                        scenarioResult(name = "Sum", tests = listOf(testEntry("sum.tinyVec"))),
                    ),
                ),
            )

            assertTrue(logs.any { it.contains("--- Correctness: Sum ---") })
            assertTrue(logs.any { it.contains("✓ sum.tinyVec ($DEFAULT_THREADS_TAG)") })
        }

        @Test
        fun `metrics line is emitted when scenario carries metrics`() {
            val logs = render(
                taskResult(
                    performance = listOf(
                        scenarioResult(
                            name = "PerfScen",
                            metrics = ScenarioMetrics(
                                t1Ms = 100.0, tpMs = 25.0, speedup = 4.0, efficiency = 0.95,
                            ),
                        ),
                    ),
                ),
            )

            assertTrue(
                logs.any { it.contains("T1=100.0ms") && it.contains("Speedup=4.00x") && it.contains("Efficiency=95%") },
            )
        }

        @Test
        fun `run stats appear in brackets when present`() {
            val logs = render(
                taskResult(
                    correctness = listOf(
                        scenarioResult(
                            tests = listOf(
                                testEntry(
                                    name = "x.y",
                                    runs = listOf(
                                        testRun(
                                            stats = runStats(
                                                timeMs = 8.5, speedup = 3.2, efficiency = 0.8,
                                                computeEfficiency = 0.7, loadBalanceRatio = 0.95,
                                            ),
                                            processStats = ProcessStats(maxRssKb = 2048L),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )

            val line = logs.single { it.contains("✓ x.y") }
            assertContains(line, "8.5ms")
            assertContains(line, "2MB")
            assertContains(line, "speedup=3.20x")
            assertContains(line, "eff=80%")
            assertContains(line, "CE=70%")
            assertContains(line, "LB=0.95")
        }

        @Test
        fun `failed run emits timeout OOM exit-code stderr and OMP counters`() {
            val logs = render(
                taskResult(
                    correctness = listOf(
                        scenarioResult(
                            tests = listOf(
                                testEntry(
                                    name = "broken.case",
                                    runs = listOf(
                                        testRun(
                                            passed = false,
                                            message = "assertion failed",
                                            processStats = ProcessStats(
                                                exitCode = 134, oomKilled = true, timedOut = true,
                                                cgMemPeakKb = 4096L, wallTimeSec = 30.5, cpuTimeSec = 28.0,
                                            ),
                                            stderrOutput = "line1\nline2",
                                            parallelStats = ParallelStats(
                                                parallelRegions = 3, forLoops = 5, taskWaits = 2, maxThreadsUsed = 4,
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )

            assertContains(logs, "       Message: assertion failed")
            assertTrue(logs.any { it.contains("⚠ TIMED OUT (wall=30.5s, cpu=28.0s)") })
            assertTrue(logs.any { it.contains("⚠ OOM KILLED (peak=4MB)") })
            assertContains(logs, "       Exit code: 134")
            assertContains(logs, "       Stderr (first 20 lines):")
            assertContains(logs, "         line1")
            assertContains(logs, "         line2")
            val ompLine = logs.single { it.startsWith("       OMP:") }
            assertContains(ompLine, "parallel=3")
            assertContains(ompLine, "for=5")
            assertContains(ompLine, "taskWaits=2")
            assertContains(ompLine, "maxThreads=4")
        }

        @Test
        fun `stderr is capped at 20 lines`() {
            val totalStderrLines = 50
            val stderr = (1..totalStderrLines).joinToString("\n") { "line$it" }
            val logs = render(
                taskResult(
                    correctness = listOf(
                        scenarioResult(
                            tests = listOf(
                                testEntry(
                                    runs = listOf(testRun(passed = false, stderrOutput = stderr)),
                                ),
                            ),
                        ),
                    ),
                ),
            )

            val stderrLines = logs.filter { it.startsWith("         line") }
            assertEquals(STDERR_CAP, stderrLines.size)
        }

        @Test
        fun `exit code of -1 and 0 are not reported`() {
            val logs = render(
                taskResult(
                    correctness = listOf(
                        scenarioResult(
                            tests = listOf(
                                testEntry(
                                    runs = listOf(
                                        testRun(
                                            passed = false,
                                            processStats = ProcessStats(exitCode = -1),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )

            assertFalse(logs.any { it.contains("Exit code:") })
        }
    }

    // --- performance skipped, threads, discovered -----------------------

    @Nested
    inner class SecondaryBlocks {

        @Test
        fun `performance skipped emits a dedicated section with the reason`() {
            val logs = render(
                taskResult(
                    performanceSkipped = true,
                    performanceSkipReason = "correctness failed",
                ),
            )

            assertTrue(logs.any { it.contains("--- Performance SKIPPED ---") })
            assertContains(logs, "  Reason: correctness failed")
        }

        @Test
        fun `performance skipped without explicit reason falls back to unknown`() {
            val logs = render(taskResult(performanceSkipped = true))

            assertContains(logs, "  Reason: unknown")
        }

        @Test
        fun `thread counts are listed per mode`() {
            val logs = render(
                taskResult(
                    threadCounts = ThreadCounts(
                        correctness = listOf(1, 2, 4),
                        performance = listOf(2, 8),
                    ),
                ),
            )

            assertContains(logs, "[threads] Correctness runs: 1, 2, 4")
            assertContains(logs, "[threads] Performance runs: 2, 8")
        }

        @Test
        fun `tests discovered lists scenarios counts and plugins`() {
            val logs = render(
                taskResult(
                    testsDiscovered = TestsDiscovered(
                        correctnessScenarios = 2, correctnessTests = 8,
                        performanceScenarios = 1, performanceTests = 4,
                        pluginsLoaded = listOf(pluginInfo("plug-a"), pluginInfo("plug-b", "error")),
                    ),
                ),
            )

            val disc = logs.single { it.startsWith("[discovered]") }
            assertContains(disc, "correctness: 2 scenarios (8 tests)")
            assertContains(disc, "performance: 1 scenarios (4 tests)")
            assertContains(logs, "[plugins] Loaded: plug-a (loaded), plug-b (error)")
        }
    }

    // --- summary + scalability ------------------------------------------

    @Nested
    inner class SummaryBlock {

        @Test
        fun `summary emits sections for both modes when both summaries are present`() {
            val logs = render(
                taskResult(
                    summary = ResultSummary(
                        correctness = testSummary(totalTests = 10, passed = 8, failed = 2),
                        performance = testSummary(totalTests = 4, passed = 4, failed = 0),
                    ),
                ),
            )

            assertTrue(logs.any { it.contains("--- Summary: Correctness ---") })
            assertTrue(logs.any { it.contains("--- Summary: Performance ---") })
            assertContains(logs, "  Tests: 8/10 passed, 2 failed")
            assertContains(logs, "  Tests: 4/4 passed, 0 failed")
        }

        @Test
        fun `failure breakdown shows only non-zero categories`() {
            val logs = render(
                taskResult(
                    summary = ResultSummary(
                        correctness = testSummary(
                            totalTests = 10, passed = 5, failed = 5,
                            failedByTimeout = 2, failedByOom = 0,
                            failedByCrash = 1, failedByCorrectness = 2,
                        ),
                    ),
                ),
            )

            val line = logs.single { it.startsWith("  Failure breakdown:") }
            assertContains(line, "2 timeout")
            assertContains(line, "1 crash")
            assertContains(line, "2 incorrect")
            assertFalse(line.contains("OOM"))
        }

        @Test
        fun `peak memory and cpu time are converted to MB and seconds`() {
            val logs = render(
                taskResult(
                    summary = ResultSummary(
                        correctness = testSummary(
                            totalTests = 1, passed = 1,
                            maxTimeMs = 42.0,
                            maxRssKb = 5120L,
                        ),
                    ),
                ),
            )

            assertContains(logs, "  Max time: 42ms")
            assertContains(logs, "  Peak RSS: 5MB")
        }

        @Test
        fun `scalability emits an aligned table`() {
            val logs = render(
                taskResult(
                    summary = ResultSummary(
                        performance = testSummary(
                            totalTests = 0,
                            scalability = listOf(
                                scalability(
                                    threads = 1,
                                    totalTimeMs = 100.0,
                                    speedup = 1.0,
                                    efficiency = 1.0,
                                    maxRssKb = 1024
                                ),
                                scalability(
                                    threads = 4,
                                    totalTimeMs = 30.0,
                                    speedup = 3.33,
                                    efficiency = 0.83,
                                    maxRssKb = 2048
                                ),
                            ),
                        ),
                    ),
                ),
            )

            assertContains(logs, "  Scalability:")
            assertTrue(
                logs.any {
                    it.contains("Threads") && it.contains("Speedup") &&
                            it.contains("Efficiency") && it.contains("Tests")
                },
            )
            val firstRow = logs.first { it.trim().startsWith("1 |") }
            assertContains(firstRow, "100ms")
            assertContains(firstRow, "1.00x")
            assertContains(firstRow, "100%")
            assertContains(firstRow, "1MB")
            // testsCompared / testsSkipped not provided -> "-"
            assertTrue(firstRow.trimEnd().endsWith("-"), "missing gating info should render as dash: $firstRow")
        }

        @Test
        fun `scalability tests column shows passed-vs-total when engine reports gating`() {
            val logs = render(
                taskResult(
                    summary = ResultSummary(
                        performance = testSummary(
                            scalability = listOf(
                                scalability(threads = 1, testsCompared = 5, testsSkipped = 0),
                                scalability(threads = 4, testsCompared = 3, testsSkipped = 2),
                            ),
                        ),
                    ),
                ),
            )

            val rows = logs.filter { it.trim().startsWith("1 |") || it.trim().startsWith("4 |") }
            assertEquals(2, rows.size)
            assertTrue(rows[0].trimEnd().endsWith("5/5"), "1-thread row tail: ${rows[0]}")
            assertTrue(rows[1].trimEnd().endsWith("3/5"), "4-thread row tail: ${rows[1]}")
        }

        @Test
        fun `summary section is skipped when scalability list is empty`() {
            val logs = render(
                taskResult(
                    summary = ResultSummary(
                        performance = testSummary(totalTests = 0, scalability = emptyList()),
                    ),
                ),
            )

            assertFalse(logs.any { it.contains("Scalability:") }, "empty scalability must not render header")
        }
    }

    // --- per-scenario summary (engine sends scenario.summary now) --------

    @Nested
    inner class PerScenarioSummary {

        @Test
        fun `scenario summary section renders after its tests`() {
            val logs = render(
                taskResult(
                    performance = listOf(
                        scenarioResult(
                            name = "VecAdd",
                            tests = listOf(testEntry("vec.tiny")),
                            summary = testSummary(
                                totalTests = 4, passed = 4, failed = 0,
                                maxTimeMs = 50.0, maxRssKb = 3072L,
                            ),
                        ),
                    ),
                ),
            )

            val scenarioIdx = logs.indexOfFirst { it.contains("--- Performance: VecAdd ---") }
            val testIdx = logs.indexOfFirst { it.contains("✓ vec.tiny") }
            val summaryIdx = logs.indexOfFirst { it.contains("--- Performance: VecAdd Summary ---") }
            assertTrue(scenarioIdx >= 0 && testIdx > scenarioIdx && summaryIdx > testIdx)
            assertContains(logs, "  Tests: 4/4 passed, 0 failed")
            assertContains(logs, "  Max time: 50ms")
            assertContains(logs, "  Peak RSS: 3MB")
        }

        @Test
        fun `scenario summary carries its own scalability table`() {
            val logs = render(
                taskResult(
                    performance = listOf(
                        scenarioResult(
                            name = "PerfA",
                            summary = testSummary(
                                scalability = listOf(
                                    scalability(threads = 1, totalTimeMs = 200.0, speedup = 1.0,
                                        efficiency = 1.0, maxRssKb = 1024L, testsCompared = 2, testsSkipped = 0),
                                    scalability(threads = 8, totalTimeMs = 30.0, speedup = 6.66,
                                        efficiency = 0.83, maxRssKb = 4096L, testsCompared = 2, testsSkipped = 0),
                                ),
                            ),
                        ),
                    ),
                ),
            )

            val summaryIdx = logs.indexOfFirst { it.contains("--- Performance: PerfA Summary ---") }
            assertTrue(summaryIdx > 0)
            val tail = logs.drop(summaryIdx)
            assertTrue(tail.any { it.contains("Scalability:") })
            assertTrue(tail.any { it.trim().startsWith("8 |") && it.contains("6.66x") })
        }

        @Test
        fun `scenario without summary leaves no Summary header`() {
            val logs = render(
                taskResult(
                    correctness = listOf(scenarioResult(name = "Quiet")),
                ),
            )

            assertFalse(logs.any { it.contains("Quiet Summary") })
        }
    }

    // --- tail marker ----------------------------------------------------

    @Test
    fun `every render ends with the Done marker`() {
        val logs = render(taskResult())

        assertEquals("[parallel] Done.", logs.last())
    }

    // --- progress events ------------------------------------------------

    @Nested
    inner class FormatProgress {

        @Test
        fun `test phase combines scenario test threadCount status time and message`() {
            val line = formatter.formatProgress(
                progressEvent(
                    phase = "test", status = "passed",
                    scenario = "sum", test = "tinyVec", threadCount = 4,
                    timeMs = 12.7, message = "ok",
                ),
            )

            assertEquals("[parallel] [test:passed] sum/tinyVec/4T (12ms) - ok", line)
        }

        @Test
        fun `test phase tolerates partial fields`() {
            val line = formatter.formatProgress(progressEvent(phase = "test", status = null))

            assertEquals("[parallel] [test:?] ", line)
        }

        @Test
        fun `non-test phase produces a simple tag with optional message`() {
            assertEquals(
                "[parallel] [resolveTests]: cloning",
                formatter.formatProgress(progressEvent(phase = "resolveTests", message = "cloning")),
            )
            assertEquals(
                "[parallel] [received]",
                formatter.formatProgress(progressEvent(phase = "received", message = null)),
            )
            assertEquals(
                "[parallel] [received]",
                formatter.formatProgress(progressEvent(phase = "received", message = "   ")),
            )
        }
    }

    companion object {
        // Default thread count used by the TaskResultBuilders helpers.
        private const val DEFAULT_THREADS_TAG = "4T"
        private const val THREADS = 4
        private const val WALL_TIME_SEC = 30
        private const val HW_THREADS = 8

        // Mirrors the formatter's stderr-cap line count (`take(20)`).
        private const val STDERR_CAP = 20
    }
}
