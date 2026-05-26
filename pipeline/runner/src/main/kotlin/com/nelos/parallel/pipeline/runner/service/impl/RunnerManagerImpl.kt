package com.nelos.parallel.pipeline.runner.service.impl

import com.nelos.parallel.pipeline.runner.exception.NoRunnerAvailableException
import com.nelos.parallel.pipeline.runner.exception.RunnerInfraException
import com.nelos.parallel.pipeline.runner.service.RunHandle
import com.nelos.parallel.pipeline.runner.service.RunnerConfigService
import com.nelos.parallel.pipeline.runner.service.RunnerContext
import com.nelos.parallel.pipeline.runner.service.RunnerManager
import com.nelos.parallel.pipeline.runner.service.TaskRunner
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.runnerManager")
class RunnerManagerImpl(
    runners: List<TaskRunner>,
    private val runnerConfigService: RunnerConfigService,
) : RunnerManager {

    // Bean name -> runner. Two TaskRunner beans with the same `name` would be a
    // configuration error - fail fast at startup so we never silently lose one.
    private val runnersByName: Map<String, TaskRunner> = runners.associateBy { it.name }
        .also { byName ->
            require(byName.size == runners.size) {
                val duplicates = runners.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
                "duplicate TaskRunner names: $duplicates"
            }
        }

    private val rrCounters = ConcurrentHashMap<Int, AtomicInteger>()

    override fun dispatch(ctx: RunnerContext): RunHandle {
        val ordered = orderedActiveRunners()
        if (ordered.isEmpty()) throw NoRunnerAvailableException(emptyList())

        val attempts = mutableListOf<NoRunnerAvailableException.Attempt>()
        for (runner in ordered) {
            if (!runner.isAvailable()) {
                attempts += NoRunnerAvailableException.Attempt(runner.name, "isAvailable=false")
                continue
            }
            val handle = try {
                runner.tryRun(ctx)
            } catch (e: RunnerInfraException) {
                LOG.warn("runner {} declined: {}", runner.name, e.message)
                attempts += NoRunnerAvailableException.Attempt(runner.name, e.message ?: "infra failure")
                continue
            } catch (e: Exception) {
                LOG.error("runner {} threw unexpectedly, falling over", runner.name, e)
                attempts += NoRunnerAvailableException.Attempt(
                    runner.name,
                    "unexpected: ${e.message ?: e::class.simpleName}"
                )
                continue
            }
            if (handle == null) {
                attempts += NoRunnerAvailableException.Attempt(runner.name, "declined")
                continue
            }
            LOG.info("submission {} accepted by runner {} ({})", ctx.submissionId, runner.name, runner.type)
            return handle
        }
        throw NoRunnerAvailableException(attempts)
    }

    override fun listRunners(): List<RunnerManager.RunnerSnapshot> {
        val byName = runnerConfigService.findAll().associateBy { it.name }
        return runnersByName.values.map { runner ->
            val entry = byName[runner.name]
            RunnerManager.RunnerSnapshot(
                name = runner.name,
                type = runner.type,
                // Default to enabled when no DB row exists (admin hasn't opted out yet).
                enabled = entry?.enabled ?: true,
                priority = entry?.priority ?: DEFAULT_PRIORITY,
                available = runner.isAvailable(),
            )
        }.sortedWith(compareBy({ !it.enabled }, { -it.priority }, { it.name }))
    }

    /**
     * Effective dispatch order:
     *   1. iterate all runner beans present in the build;
     *   2. for each, read the `prl_runner_config` row if any. Missing row =
     *      `enabled=true, priority=0` (default). Explicit `enabled=false`
     *      drops the runner;
     *   3. group by priority descending (higher value runs first);
     *   4. within each group, round-robin starting from the next position
     *      (per-priority counter, atomic incremented every dispatch).
     */
    private fun orderedActiveRunners(): List<TaskRunner> {
        val byName = runnerConfigService.findAll().associateBy { it.name }
        val byPriority = sortedMapOf<Int, MutableList<TaskRunner>>(compareByDescending { it })
        for (runner in runnersByName.values) {
            val cfg = byName[runner.name]
            if (cfg != null && !cfg.enabled) continue
            val priority = cfg?.priority ?: DEFAULT_PRIORITY
            byPriority.getOrPut(priority) { mutableListOf() } += runner
        }
        if (byPriority.isEmpty()) return emptyList()

        val ordered = ArrayList<TaskRunner>()
        for ((priority, runners) in byPriority) {
            val sorted = runners.sortedBy { it.name }   // deterministic ordering before rotation
            if (sorted.size == 1) {
                ordered += sorted
                continue
            }
            val counter = rrCounters.computeIfAbsent(priority) { AtomicInteger(0) }
            val start = (counter.getAndIncrement() % sorted.size).let { if (it < 0) it + sorted.size else it }
            for (i in sorted.indices) ordered += sorted[(start + i) % sorted.size]
        }
        return ordered
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RunnerManagerImpl::class.java)
        // Default priority for runners with no DB row. 0 mirrors the column
        // default in `prl_runner_config`.
        private const val DEFAULT_PRIORITY = 0
    }
}
