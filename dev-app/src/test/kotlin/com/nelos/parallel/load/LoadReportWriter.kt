package com.nelos.parallel.load

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Writes the load-test markdown report: environment + SLO targets + discovery
 * scan + adaptive steady-state ladder + operating point verdict.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class LoadReportWriter(private val target: Path) {

    data class Env(
        val cpuThreads: Int,
        val heapMaxMb: Long,
        val tomcatThreadsMax: Int,
        val hikariPoolSize: Int,
        val taskDurationMs: Long,
        val callbackPoolSize: Int,
    )

    data class Slo(
        val submitP95Ms: Double,
        val submitP99Ms: Double,
        val e2eP95Ms: Double,
        val errorRatePct: Double,
    )

    data class StepResult(
        val rps: Int,
        val durationSec: Int,
        val snapshot: LoadStats.Snapshot,
        /** "discovery", "steady", "refine" - controls table grouping. */
        val phase: String,
    )

    data class CombinedReport(
        val discovery: List<StepResult>,
        val steadyState: List<StepResult>,
        val slo: Slo,
    )

    fun write(env: Env, report: CombinedReport) {
        val sb = StringBuilder()
        sb.append("# Pipeline Load Test Report - ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .append('\n').append('\n')

        sb.append("## Environment\n")
        sb.append("- CPU threads: ").append(env.cpuThreads).append('\n')
        sb.append("- JVM heap max: ").append(env.heapMaxMb).append(" MB\n")
        sb.append("- Tomcat threads.max: ").append(env.tomcatThreadsMax).append('\n')
        sb.append("- Hikari pool size: ").append(env.hikariPoolSize).append('\n')
        sb.append("- Engine taskDuration: ").append(env.taskDurationMs)
            .append("ms, callback-pool-size: ").append(env.callbackPoolSize).append('\n').append('\n')

        sb.append("## SLO targets (declared up front)\n")
        sb.append("- Submit p95 <= ").append(report.slo.submitP95Ms.toInt()).append(" ms\n")
        sb.append("- Submit p99 <= ").append(report.slo.submitP99Ms.toInt()).append(" ms\n")
        sb.append("- E2E p95 <= ").append(report.slo.e2eP95Ms.toInt())
            .append(" ms (engine baseline ").append(env.taskDurationMs).append("ms + queue tolerance)\n")
        sb.append("- Error rate < ").append(String.format(Locale.ROOT, "%.1f", report.slo.errorRatePct)).append("%\n\n")

        sb.append("## Discovery scan\n")
        sb.append("Quick ramp at predefined RPS levels to surface the rough knee. Open-loop pacing, latency from scheduled time. No verdict - informational.\n\n")
        renderTable(sb, report.discovery, report.slo, withVerdict = false)

        sb.append("## Steady-state ladder (adaptive)\n")
        sb.append("Each level held for ").append(report.steadyState.firstOrNull()?.durationSec ?: 0)
            .append(" s. Starts at the configured start-rps and multiplies by step-multiplier on each PASS; stops on first FAIL; refinement runs at midpoint between last-pass and first-fail.\n\n")
        renderTable(sb, report.steadyState, report.slo, withVerdict = true)

        val operating = report.steadyState
            .filter { slosMet(it, report.slo) }
            .maxByOrNull { it.rps }
        val firstFail = report.steadyState.firstOrNull { !slosMet(it, report.slo) }
        sb.append("**Operating point** (max steady-state RPS meeting all SLOs): ")
        if (operating != null) sb.append(operating.rps).append(" RPS")
        else sb.append("none - even the lowest level breaches an SLO")
        sb.append('\n')
        if (firstFail != null) {
            sb.append("**Ceiling** (first RPS breaching SLO): ").append(firstFail.rps).append(" RPS\n")
        } else {
            sb.append("**Ceiling** not reached within max-rps - try increasing max-rps to find it.\n")
        }

        Files.writeString(target, sb.toString())
    }

    private fun renderTable(sb: StringBuilder, rows: List<StepResult>, slo: Slo, withVerdict: Boolean) {
        sb.append("| Phase | RPS | Target | Issued | Errors | err% | Submit p50/p95/p99 ms | E2E p50/p95/p99 ms | Completed | Peak in-flight |")
        if (withVerdict) sb.append(" SLO |")
        sb.append("\n|:--|---:|---:|---:|---:|---:|:--|:--|---:|---:|")
        if (withVerdict) sb.append(":--:|")
        sb.append('\n')
        for (step in rows) {
            val s = step.snapshot
            val errPct = if (s.submitTotal == 0L) 0.0 else s.submitErrors * 100.0 / s.submitTotal
            sb.append('|').append(step.phase)
                .append('|').append(step.rps)
                .append('|').append(s.targetIssued)
                .append('|').append(s.submitTotal)
                .append('|').append(s.submitErrors)
                .append('|').append(String.format(Locale.ROOT, "%.2f", errPct))
                .append('|').append(latencyTriple(s.submitLatency))
                .append('|').append(latencyTriple(s.e2eLatency))
                .append('|').append(s.completed)
                .append('|').append(s.inflightPeak)
                .append('|')
            if (withVerdict) {
                sb.append(if (slosMet(step, slo)) " PASS " else " FAIL ").append('|')
            }
            sb.append('\n')
        }
        sb.append('\n')
    }

    private fun latencyTriple(l: LoadStats.LatencySummary): String =
        String.format(Locale.ROOT, "%.0f / %.0f / %.0f", l.p50Ms, l.p95Ms, l.p99Ms)

    private fun slosMet(step: StepResult, slo: Slo): Boolean {
        val s = step.snapshot
        val errPct = if (s.submitTotal == 0L) 100.0 else s.submitErrors * 100.0 / s.submitTotal
        if (errPct >= slo.errorRatePct) return false
        if (s.submitLatency.p95Ms > slo.submitP95Ms) return false
        if (s.submitLatency.p99Ms > slo.submitP99Ms) return false
        if (s.e2eLatency.p95Ms > slo.e2eP95Ms && s.e2eLatency.count > 0) return false
        return true
    }
}
