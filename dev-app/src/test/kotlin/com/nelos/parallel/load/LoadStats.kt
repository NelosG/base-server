package com.nelos.parallel.load

import org.HdrHistogram.ConcurrentHistogram
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe per-step accumulator. Workers call recordSubmitSuccess /
 * recordSubmitError with latency-from-scheduled-time so we honor open-loop
 * measurement (queue wait counts as part of latency, no coordinated omission).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class LoadStats(val scenarioName: String) {

    private val submitHistogram = ConcurrentHistogram(1, 600_000_000_000L, 3)
    private val e2eHistogram = ConcurrentHistogram(1, 600_000_000_000L, 3)

    private val submitSuccess = AtomicLong()
    private val submitErrors = AtomicLong()
    private val errorReasons = ConcurrentHashMap<String, AtomicLong>()
    private val completed = AtomicLong()
    private val drainTimedOut = AtomicLong()
    private val targetIssued = AtomicLong()

    private val inflight = AtomicInteger()
    @Volatile private var inflightPeakValue: Int = 0
    private val peakLock = Any()

    fun recordTargetIssued(count: Long) { targetIssued.set(count) }

    fun recordSubmitSuccess(submissionId: Long, latencyNs: Long) {
        submitHistogram.recordValue(latencyNs.coerceAtLeast(1))
        submitSuccess.incrementAndGet()
        val current = inflight.incrementAndGet()
        synchronized(peakLock) {
            if (current > inflightPeakValue) inflightPeakValue = current
        }
    }

    fun recordSubmitError(latencyNs: Long, reason: String) {
        submitHistogram.recordValue(latencyNs.coerceAtLeast(1))
        submitErrors.incrementAndGet()
        errorReasons.computeIfAbsent(reason) { AtomicLong() }.incrementAndGet()
    }

    fun recordE2E(submissionId: Long, terminalLatencyNs: Long) {
        e2eHistogram.recordValue(terminalLatencyNs.coerceAtLeast(1))
        completed.incrementAndGet()
        inflight.decrementAndGet()
    }

    fun recordDrainTimeout(leftoverCount: Long) {
        drainTimedOut.addAndGet(leftoverCount)
    }

    fun snapshot(): Snapshot = Snapshot(
        scenarioName = scenarioName,
        targetIssued = targetIssued.get(),
        submitSuccess = submitSuccess.get(),
        submitErrors = submitErrors.get(),
        submitTotal = submitSuccess.get() + submitErrors.get(),
        completed = completed.get(),
        inflightPeak = inflightPeakValue,
        drainTimedOut = drainTimedOut.get(),
        errorReasons = errorReasons.mapValues { it.value.get() },
        submitLatency = LatencySummary.from(submitHistogram),
        e2eLatency = LatencySummary.from(e2eHistogram),
    )

    data class Snapshot(
        val scenarioName: String,
        val targetIssued: Long,
        val submitSuccess: Long,
        val submitErrors: Long,
        val submitTotal: Long,
        val completed: Long,
        val inflightPeak: Int,
        val drainTimedOut: Long,
        val errorReasons: Map<String, Long>,
        val submitLatency: LatencySummary,
        val e2eLatency: LatencySummary,
    )

    data class LatencySummary(
        val count: Long,
        val p50Ms: Double, val p95Ms: Double, val p99Ms: Double, val maxMs: Double,
    ) {
        companion object {
            fun from(h: ConcurrentHistogram): LatencySummary {
                val copy = h.copy()
                return LatencySummary(
                    count = copy.totalCount,
                    p50Ms = copy.getValueAtPercentile(50.0) / 1_000_000.0,
                    p95Ms = copy.getValueAtPercentile(95.0) / 1_000_000.0,
                    p99Ms = copy.getValueAtPercentile(99.0) / 1_000_000.0,
                    maxMs = copy.maxValue / 1_000_000.0,
                )
            }
        }
    }
}
