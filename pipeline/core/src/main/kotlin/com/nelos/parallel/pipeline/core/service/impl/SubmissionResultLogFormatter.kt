package com.nelos.parallel.pipeline.core.service.impl

import com.nelos.parallel.commons.adapter.vo.request.ProgressEvent
import com.nelos.parallel.commons.adapter.vo.response.ScenarioResult
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.commons.adapter.vo.response.TestRun
import org.springframework.stereotype.Service
import java.util.*

/**
 * Renders engine [TaskResult] / [ProgressEvent] payloads into the
 * `[parallel] ...` log lines that get persisted on a submission and shown
 * back in the CI job log + instructor/student UI. No DB or service deps -
 * pure data -> text.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.submissionResultLogFormatter")
class SubmissionResultLogFormatter {

    fun appendResultLogs(logs: MutableList<String>, r: TaskResult) {
        fun line(s: String) = logs.add(s)
        fun section(title: String) {
            line(""); line("--- $title ---")
        }

        line("")
        line("============================================")
        line("  Test execution finished: ${r.status.uppercase()}")
        line("============================================")

        r.solution?.let { line("[info] Solution: $it") }
        r.nodeId?.let { line("[info] Engine node: $it") }
        r.durationMs?.let { line("[info] Total duration: %.0fms".format(it)) }
        r.environment?.let { env ->
            val parts = listOfNotNull(
                env.platform?.let { "platform=$it" },
                env.hardwareThreads?.let { "hw_threads=$it" },
            )
            if (parts.isNotEmpty()) line("[env] ${parts.joinToString(", ")}")
        }
        r.effectiveParams?.let { p ->
            val parts = listOfNotNull(
                p.mode?.let { "mode=$it" },
                p.threads?.let { "threads=$it" },
                p.memoryLimitMb?.let { "memory=${it}MB" },
                p.wallTimeSec?.let { "wall_time=${it}s" },
                p.cpuTimeSec?.let { "cpu_time=${it}s" },
            )
            if (parts.isNotEmpty()) line("[params] ${parts.joinToString(", ")}")
        }
        r.assignmentConfig?.let { c ->
            val parts = listOfNotNull(
                c.name?.let { "name=$it" },
                c.framework?.let { "framework=$it" },
                c.mode?.let { "mode=$it" },
                c.correctnessMode?.let { "correctness=$it" },
                c.allowedFrameworks?.takeIf { it.isNotEmpty() }?.let { "allowedFrameworks=${it.joinToString("/")}" },
                c.allowedPackages?.takeIf { it.isNotEmpty() }?.let { "allowedPackages=${it.joinToString("/")}" },
            )
            if (parts.isNotEmpty()) line("[config] ${parts.joinToString(", ")}")
        }

        r.buildInfo?.let { b ->
            b.pluginLoadError?.let { line("[build] Plugin load error: $it") }
        }

        r.pipeline?.let { steps ->
            section("Pipeline Steps")
            steps.forEach { s ->
                val icon = if (s.status == "ok") "✓" else "✗"
                line("  $icon ${s.step} - ${s.status} (%.0fms)".format(s.durationMs ?: 0.0))
            }
            r.totalTimeMs?.let { line("  Total: %.0fms".format(it)) }
        }

        r.failedStep?.let { step ->
            section("FAILURE at step: $step")
            STEP_HINTS[step]?.let { line("[hint] $it") }
            r.error?.let { line("[error] $it") }
            r.errorDetails?.let { d ->
                d.violations?.let { line("[error] Forbidden libs: ${it.joinToString(", ")}") }
                d.allowedPackages?.let { line("[error] Allowed packages: ${it.joinToString(", ")}") }
                d.allowedFrameworks?.let { line("[error] Allowed frameworks: ${it.joinToString(", ")}") }
                d.framework?.let { line("[error] Framework: $it") }
            }
        }

        r.buildOutput?.let { out ->
            section("Build Output")
            out.lines().forEach { line("  $it") }
        }

        r.correctness?.forEach { logScenario(logs, "Correctness", it) }
        r.performance?.forEach { logScenario(logs, "Performance", it) }
        if (r.performanceSkipped == true) {
            section("Performance SKIPPED")
            line("  Reason: ${r.performanceSkipReason ?: "unknown"}")
        }

        r.threadCounts?.let { tc ->
            tc.correctness?.let { line("[threads] Correctness runs: ${it.joinToString(", ")}") }
            tc.performance?.let { line("[threads] Performance runs: ${it.joinToString(", ")}") }
        }

        r.testsDiscovered?.let { td ->
            val parts = listOfNotNull(
                td.correctnessScenarios?.let { "correctness: $it scenarios (${td.correctnessTests ?: "?"} tests)" },
                td.performanceScenarios?.let { "performance: $it scenarios (${td.performanceTests ?: "?"} tests)" },
            )
            if (parts.isNotEmpty()) line("[discovered] ${parts.joinToString(", ")}")
            td.pluginsLoaded?.let { plugins ->
                line("[plugins] Loaded: ${plugins.joinToString(", ") { "${it.name ?: "?"} (${it.status ?: "?"})" }}")
            }
        }

        r.summary?.let { sum ->
            listOfNotNull(
                sum.correctness?.let { "Correctness" to it },
                sum.performance?.let { "Performance" to it },
            ).forEach { (mode, s) ->
                section("Summary: $mode")
                line("  Tests: ${s.passed ?: 0}/${s.totalTests ?: 0} passed, ${s.failed ?: 0} failed")
                val categories = listOfNotNull(
                    s.failedByTimeout?.takeIf { it > 0 }?.let { "$it timeout" },
                    s.failedByOom?.takeIf { it > 0 }?.let { "$it OOM" },
                    s.failedByCrash?.takeIf { it > 0 }?.let { "$it crash" },
                    s.failedByCorrectness?.takeIf { it > 0 }?.let { "$it incorrect" },
                )
                if (categories.isNotEmpty()) line("  Failure breakdown: ${categories.joinToString(", ")}")
                s.maxTimeMs?.let { line("  Max time: %.0fms".format(it)) }
                s.maxRssKb?.let { line("  Peak RSS: ${it / 1024}MB") }
                s.maxCgMemPeakKb?.let { line("  Peak cgroup memory: ${it / 1024}MB") }
                s.totalCpuTimeSec?.let { line("  Total CPU time: %.2fs".format(Locale.ROOT, it)) }
                s.scalability?.let { pts ->
                    line("  Scalability:")
                    line("    Threads | Time      | Speedup | Efficiency | CPU time  | Memory")
                    line("    --------|-----------|---------|------------|-----------|-------")
                    pts.forEach { p ->
                        line(
                            "    %7d | %7.0fms | %5.2fx  | %8.0f%%  | %7.2fs  | %dMB".format(
                                Locale.ROOT,
                                p.threads ?: 0, p.totalTimeMs ?: 0.0, p.speedup ?: 0.0,
                                (p.efficiency ?: 0.0) * 100, p.totalCpuTimeSec ?: 0.0,
                                (p.maxRssKb ?: 0) / 1024,
                            )
                        )
                    }
                }
            }
        }

        line("")
        line("[parallel] Done.")
    }

    fun formatProgress(event: ProgressEvent): String {
        if (event.phase == "test") {
            val parts = mutableListOf<String>()
            event.scenario?.let { parts.add(it) }
            event.test?.let { parts.add(it) }
            event.threadCount?.let { parts.add("${it}T") }
            val tag = parts.joinToString("/")
            val time = event.timeMs?.let { " (${it.toLong()}ms)" } ?: ""
            val msg = event.message?.takeIf { it.isNotBlank() }?.let { " - $it" } ?: ""
            return "[parallel] [test:${event.status ?: "?"}] $tag$time$msg"
        }
        val msg = event.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
        return "[parallel] [${event.phase}]$msg"
    }

    private fun logScenario(logs: MutableList<String>, label: String, scenario: ScenarioResult) {
        logs.add("")
        logs.add("--- $label: ${scenario.name} ---")
        scenario.metrics?.let { m ->
            logs.add(
                "  T1=%.1fms  Tp=%.1fms  Speedup=%.2fx  Efficiency=%.0f%%".format(
                    Locale.ROOT, m.t1Ms, m.tpMs, m.speedup, m.efficiency * 100
                )
            )
        }
        scenario.tests.forEach { test ->
            test.runs.forEach { run ->
                val icon = if (run.passed) "✓" else "✗"
                val parts = mutableListOf<String>()
                run.stats?.let { parts.add("%.1fms".format(Locale.ROOT, it.timeMs)) }
                run.processStats?.maxRssKb?.let { parts.add("${it / 1024}MB") }
                run.stats?.speedup?.let { parts.add("speedup=%.2fx".format(Locale.ROOT, it)) }
                run.stats?.efficiency?.let { parts.add("eff=%.0f%%".format(Locale.ROOT, it * 100)) }
                run.stats?.computeEfficiency?.let { parts.add("CE=%.0f%%".format(Locale.ROOT, it * 100)) }
                run.stats?.loadBalanceRatio?.let { parts.add("LB=%.2f".format(Locale.ROOT, it)) }
                val detail = if (parts.isNotEmpty()) " [${parts.joinToString(", ")}]" else ""
                logs.add("  $icon ${test.name} (${run.threads}T)$detail")
                if (!run.passed) logFailedRun(logs, run)
            }
        }
    }

    private fun logFailedRun(logs: MutableList<String>, run: TestRun) {
        run.message?.let { logs.add("       Message: $it") }
        run.processStats?.let { ps ->
            if (ps.timedOut == true) logs.add(
                "       ⚠ TIMED OUT (wall=%.1fs, cpu=%.1fs)".format(
                    Locale.ROOT, ps.wallTimeSec ?: 0.0, ps.cpuTimeSec ?: 0.0
                )
            )
            if (ps.oomKilled == true) logs.add("       ⚠ OOM KILLED (peak=${ps.cgMemPeakKb?.let { "${it / 1024}MB" } ?: "?"})")
            if (ps.exitCode != null && ps.exitCode != 0 && ps.exitCode != -1)
                logs.add("       Exit code: ${ps.exitCode}")
        }
        run.stderrOutput?.takeIf { it.isNotBlank() }?.let { stderr ->
            logs.add("       Stderr (first 20 lines):")
            stderr.lines().take(20).forEach { logs.add("         $it") }
        }
        run.parallelStats?.let { ps ->
            val counters = listOfNotNull(
                ps.parallelRegions?.takeIf { it > 0 }?.let { "parallel=$it" },
                ps.forLoops?.takeIf { it > 0 }?.let { "for=$it" },
                ps.barriers?.takeIf { it > 0 }?.let { "barrier=$it" },
                ps.criticals?.takeIf { it > 0 }?.let { "critical=$it" },
                ps.atomics?.takeIf { it > 0 }?.let { "atomic=$it" },
                ps.tasksCreated?.takeIf { it > 0 }?.let { "tasks=$it" },
                ps.taskWaits?.takeIf { it > 0 }?.let { "taskWaits=$it" },
                ps.taskGroups?.takeIf { it > 0 }?.let { "taskGroups=$it" },
                ps.singleRegions?.takeIf { it > 0 }?.let { "single=$it" },
                ps.sections?.takeIf { it > 0 }?.let { "sections=$it" },
                ps.ordered?.takeIf { it > 0 }?.let { "ordered=$it" },
                ps.masters?.takeIf { it > 0 }?.let { "masters=$it" },
                ps.simdConstructs?.takeIf { it > 0 }?.let { "simd=$it" },
                ps.flushes?.takeIf { it > 0 }?.let { "flushes=$it" },
                ps.cancels?.takeIf { it > 0 }?.let { "cancels=$it" },
                ps.taskYields?.takeIf { it > 0 }?.let { "taskYields=$it" },
                ps.maxThreadsUsed?.let { "maxThreads=$it" },
            )
            if (counters.isNotEmpty()) logs.add("       OMP: ${counters.joinToString(", ")}")
        }
    }

    companion object {
        private val STEP_HINTS = mapOf(
            "resolveTests" to "Could not clone/fetch the test repository. Check git URL, branch, and access token.",
            "resolveSolution" to "Could not clone/fetch the solution repository. Check git URL, branch, and access token.",
            "parseConfig" to "Failed to parse assignment config.json on the engine. This is likely an instructor issue.",
            "detectFramework" to "Engine could not detect a supported test framework for the assignment.",
            "validation" to "Student solution uses forbidden libraries/dependencies.",
            "buildRunner" to "Student code failed to compile. See build output below.",
            "buildPlugins" to "Test plugins failed to compile. This is likely an instructor issue.",
            "loadPlugins" to "Engine failed to load test plugins (DLL load or scenario JSON error).",
            "runCorrectness" to "Some correctness tests failed. Performance tests were skipped.",
            "runPerformance" to "Some performance tests failed.",
        )
    }
}
