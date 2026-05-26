package com.nelos.parallel.load

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoadStatsTest {

    @Test
    fun `records submit success, error, and percentile latencies`() {
        val s = LoadStats("scenario-A")
        s.recordSubmitSuccess(submissionId = 1L, latencyNs = 5_000_000)         // 5 ms
        s.recordSubmitSuccess(submissionId = 2L, latencyNs = 15_000_000)        // 15 ms
        s.recordSubmitSuccess(submissionId = 3L, latencyNs = 100_000_000)       // 100 ms
        s.recordSubmitError(latencyNs = 200_000_000, reason = "503")            // 200 ms

        val snap = s.snapshot()
        assertEquals(3, snap.submitSuccess)
        assertEquals(1, snap.submitErrors)
        assertEquals(4, snap.submitTotal)
        // p50 of {5, 15, 100, 200} ms - HdrHistogram returns the bucket containing p50.
        // For 4 values, p50 sits at index 1 or 2; expect roughly 15ms.
        assertTrue(snap.submitLatency.p50Ms in 14.0..16.0, "p50 was ${snap.submitLatency.p50Ms}")
        assertTrue(snap.submitLatency.maxMs >= 200.0)
    }

    @Test
    fun `records e2e latency once per submissionId`() {
        val s = LoadStats("scenario-B")
        s.recordSubmitSuccess(submissionId = 10L, latencyNs = 1_000_000)
        s.recordE2E(submissionId = 10L, terminalLatencyNs = 5_000_000_000)      // 5 s

        val snap = s.snapshot()
        assertEquals(1, snap.completed)
        assertTrue(snap.e2eLatency.p50Ms in 4900.0..5100.0)
    }

    @Test
    fun `inflight peak grows with submit then drops on completion`() {
        val s = LoadStats("scenario-C")
        s.recordSubmitSuccess(1L, 0); s.recordSubmitSuccess(2L, 0); s.recordSubmitSuccess(3L, 0)
        s.recordE2E(1L, 0)

        assertEquals(3, s.snapshot().inflightPeak)
    }
}
