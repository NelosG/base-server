package com.nelos.parallel.pipeline.core.service.impl

import com.nelos.parallel.commons.adapter.vo.request.ProgressEvent
import com.nelos.parallel.commons.adapter.vo.response.*

/**
 * Small VO builders for log-formatter and evaluator tests. Each helper has
 * one required argument (the bit the test actually cares about) and sane
 * defaults for everything else so tests stay readable.
 */

fun taskResult(
    jobId: String = "job-1",
    status: String = "completed",
    nodeId: String? = null,
    error: String? = null,
    durationMs: Double? = null,
    solution: String? = null,
    buildOutput: String? = null,
    correctness: List<ScenarioResult>? = null,
    performance: List<ScenarioResult>? = null,
    buildInfo: BuildInfo? = null,
    threadCounts: ThreadCounts? = null,
    totalTimeMs: Double? = null,
    assignmentConfig: AssignmentConfig? = null,
    pipeline: List<PipelineStep>? = null,
    effectiveParams: EffectiveParams? = null,
    environment: EnvironmentInfo? = null,
    failedStep: String? = null,
    errorDetails: ErrorDetails? = null,
    testsDiscovered: TestsDiscovered? = null,
    summary: ResultSummary? = null,
    performanceSkipped: Boolean? = null,
    performanceSkipReason: String? = null,
): TaskResult = TaskResult(
    jobId = jobId, nodeId = nodeId, status = status, error = error,
    durationMs = durationMs, solution = solution, buildOutput = buildOutput,
    correctness = correctness, performance = performance, buildInfo = buildInfo,
    threadCounts = threadCounts, totalTimeMs = totalTimeMs,
    assignmentConfig = assignmentConfig, pipeline = pipeline,
    effectiveParams = effectiveParams, environment = environment,
    failedStep = failedStep, errorDetails = errorDetails,
    testsDiscovered = testsDiscovered, summary = summary,
    performanceSkipped = performanceSkipped, performanceSkipReason = performanceSkipReason,
)

fun pipelineStep(step: String, status: String = "ok", durationMs: Double = 12.0) =
    PipelineStep(step = step, status = status, durationMs = durationMs)

fun scenarioResult(
    name: String = "Sum",
    tests: List<TestEntry> = listOf(testEntry()),
    metrics: ScenarioMetrics? = null,
    summary: TestSummary? = null,
) = ScenarioResult(name = name, tests = tests, metrics = metrics, summary = summary)

fun testEntry(name: String = "sum.basic", runs: List<TestRun> = listOf(testRun())) =
    TestEntry(name = name, runs = runs)

fun testRun(
    threads: Int = 4,
    passed: Boolean = true,
    message: String? = null,
    stats: RunStats? = null,
    parallelStats: ParallelStats? = null,
    processStats: ProcessStats? = null,
    stderrOutput: String? = null,
) = TestRun(
    threads = threads, passed = passed, message = message,
    stats = stats, parallelStats = parallelStats,
    processStats = processStats, stderrOutput = stderrOutput,
)

fun runStats(
    timeMs: Double = 12.5,
    speedup: Double = 3.5,
    efficiency: Double = 0.875,
    computeEfficiency: Double? = null,
    loadBalanceRatio: Double? = null,
) = RunStats(
    timeMs = timeMs, workMs = 50.0, spanMs = 14.0,
    parallelism = 3.57, speedup = speedup, efficiency = efficiency,
    computeEfficiency = computeEfficiency, loadBalanceRatio = loadBalanceRatio,
)

fun testSummary(
    totalTests: Int? = null,
    passed: Int? = null,
    failed: Int? = null,
    failedByTimeout: Int? = null,
    failedByOom: Int? = null,
    failedByCrash: Int? = null,
    failedByCorrectness: Int? = null,
    maxTimeMs: Double? = null,
    maxRssKb: Long? = null,
    maxCgMemPeakKb: Long? = null,
    totalCpuTimeSec: Double? = null,
    scalability: List<ScalabilityPoint>? = null,
) = TestSummary(
    totalTests = totalTests, passed = passed, failed = failed,
    failedByTimeout = failedByTimeout, failedByOom = failedByOom,
    failedByCrash = failedByCrash, failedByCorrectness = failedByCorrectness,
    maxTimeMs = maxTimeMs, maxRssKb = maxRssKb,
    maxCgMemPeakKb = maxCgMemPeakKb, totalCpuTimeSec = totalCpuTimeSec,
    scalability = scalability,
)

fun scalability(
    threads: Int, totalTimeMs: Double = 100.0, speedup: Double = 2.0,
    efficiency: Double = 0.5, maxRssKb: Long = 1024L, totalCpuTimeSec: Double = 0.5,
    testsCompared: Int? = null, testsSkipped: Int? = null,
) = ScalabilityPoint(
    threads = threads, totalTimeMs = totalTimeMs, speedup = speedup,
    efficiency = efficiency, maxRssKb = maxRssKb, totalCpuTimeSec = totalCpuTimeSec,
    testsCompared = testsCompared, testsSkipped = testsSkipped,
)

fun progressEvent(
    phase: String = "test",
    status: String? = null,
    scenario: String? = null,
    test: String? = null,
    threadCount: Int? = null,
    timeMs: Double? = null,
    message: String? = null,
    progress: Double? = null,
) = ProgressEvent(
    jobId = "job-1", phase = phase, status = status,
    scenario = scenario, test = test, threadCount = threadCount,
    timeMs = timeMs, message = message, progress = progress,
)

fun pluginInfo(name: String, status: String = "loaded") = PluginInfo(name = name, status = status)
