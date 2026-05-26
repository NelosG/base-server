package com.nelos.parallel.load

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Open-loop submit generator: fires requests at constant arrival rate
 * regardless of system response. Latency is measured from the scheduled time
 * (not from worker pickup) so queue wait shows in the metrics - this avoids
 * coordinated omission.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class ScenarioRunner(
    private val baseUrl: String,
    private val apiKey: String,
    private val rest: TestRestTemplate,
    private val gitlabProjectPath: String,
    private val studentLogins: List<String>,
    private val mrIidCounter: AtomicLong,
    private val objectMapper: ObjectMapper,
    private val tracker: InflightTracker,
    private val submitPoolSize: Int,
) {

    fun runOpenLoop(stats: LoadStats, targetRps: Int, durationSec: Int) {
        val totalCount = targetRps.toLong() * durationSec
        stats.recordTargetIssued(totalCount)
        val intervalNs = TimeUnit.SECONDS.toNanos(1) / targetRps.coerceAtLeast(1)
        val pool: ExecutorService = Executors.newFixedThreadPool(submitPoolSize) { r ->
            Thread(r, "loadtest-client").apply { isDaemon = true }
        }
        val startNs = System.nanoTime()
        try {
            for (i in 0 until totalCount) {
                val scheduledAtNs = startNs + intervalNs * i
                val nowNs = System.nanoTime()
                if (scheduledAtNs > nowNs) {
                    val deltaNs = scheduledAtNs - nowNs
                    Thread.sleep(deltaNs / 1_000_000, (deltaNs % 1_000_000).toInt())
                }
                pool.submit { submitOne(stats, scheduledAtNs) }
            }
        } finally {
            pool.shutdown()
            pool.awaitTermination(durationSec.toLong() + 60, TimeUnit.SECONDS)
        }
    }

    private fun submitOne(stats: LoadStats, scheduledAtNs: Long) {
        val student = studentLogins.random()
        val mrIid = mrIidCounter.incrementAndGet()
        val body = mapOf(
            "projectPath" to gitlabProjectPath,
            "mrIid" to mrIid,
            "sourceRepoUrl" to "http://stub/$student/lab1.git",
            "sourceBranch" to "main",
            "commitSha" to "abc1234",
            "username" to student,
        )
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-API-Key", apiKey)
        }
        val response = try {
            rest.exchange(
                "$baseUrl/api/pipeline/submit", HttpMethod.POST,
                HttpEntity(body, headers), String::class.java,
            )
        } catch (e: Exception) {
            val elapsedNs = System.nanoTime() - scheduledAtNs
            stats.recordSubmitError(elapsedNs, e::class.simpleName ?: "exception")
            return
        }
        val elapsedNs = System.nanoTime() - scheduledAtNs
        if (response.statusCode == HttpStatus.OK) {
            val body2 = response.body
            val submissionId = if (body2.isNullOrBlank()) null
            else runCatching { objectMapper.readTree(body2).get("submissionId")?.asLong(0L)?.takeIf { it > 0 } }
                .getOrNull()
            if (submissionId == null) {
                stats.recordSubmitError(elapsedNs, "unparseable")
                return
            }
            stats.recordSubmitSuccess(submissionId, elapsedNs)
            tracker.register(submissionId, scheduledAtNs)
        } else {
            stats.recordSubmitError(elapsedNs, "http-${response.statusCode.value()}")
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ScenarioRunner::class.java)
    }
}
