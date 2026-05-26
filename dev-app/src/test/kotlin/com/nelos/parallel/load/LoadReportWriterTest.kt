package com.nelos.parallel.load

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

class LoadReportWriterTest {

    @Test
    fun `writes report with discovery, steady-state, operating point and ceiling`(@TempDir tmp: Path) {
        val target = tmp.resolve("report.md")
        val writer = LoadReportWriter(target)
        val env = LoadReportWriter.Env(
            cpuThreads = 16, heapMaxMb = 2048, tomcatThreadsMax = 200, hikariPoolSize = 50,
            taskDurationMs = 5000, callbackPoolSize = 1000,
        )
        val slo = LoadReportWriter.Slo(
            submitP95Ms = 10000.0, submitP99Ms = 30000.0,
            e2eP95Ms = 30000.0, errorRatePct = 1.0,
        )
        val ok = step("steady", rps = 50, latencyNs = 50_000_000, e2eNs = 5_500_000_000L)
        val bad = step("steady", rps = 200, latencyNs = 20_000_000_000L, e2eNs = 50_000_000_000L)

        writer.write(env, LoadReportWriter.CombinedReport(
            discovery = listOf(step("discovery", rps = 10, latencyNs = 30_000_000, e2eNs = 5_100_000_000L)),
            steadyState = listOf(ok, bad),
            slo = slo,
        ))

        val content = target.toFile().readText()
        assertContains(content, "# Pipeline Load Test Report")
        assertContains(content, "## SLO targets")
        assertContains(content, "## Discovery scan")
        assertContains(content, "## Steady-state ladder")
        assertTrue(content.contains("PASS"), "expected at least one PASS row")
        assertTrue(content.contains("FAIL"), "expected at least one FAIL row")
        assertContains(content, "Operating point")
        assertContains(content, "Ceiling")
    }

    private fun step(phase: String, rps: Int, latencyNs: Long, e2eNs: Long): LoadReportWriter.StepResult {
        val stats = LoadStats("$phase-$rps")
        stats.recordTargetIssued(50L)
        repeat(50) { stats.recordSubmitSuccess(it.toLong(), latencyNs) }
        repeat(50) { stats.recordE2E(it.toLong(), e2eNs) }
        return LoadReportWriter.StepResult(rps = rps, durationSec = 60, snapshot = stats.snapshot(), phase = phase)
    }
}
