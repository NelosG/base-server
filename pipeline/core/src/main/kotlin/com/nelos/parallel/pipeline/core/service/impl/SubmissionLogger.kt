package com.nelos.parallel.pipeline.core.service.impl

import com.nelos.parallel.pipeline.data.entity.SubmissionLog
import com.nelos.parallel.pipeline.data.service.SubmissionLogService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Appends incremental log lines for a submission. Used by submit, result handler,
 * progress callback, and timeout cleanup.
 *
 * Propagation is `REQUIRED`: log writes join the caller's transaction so a freshly
 * inserted (still uncommitted) submission row is visible to the FK check on
 * `prl_submission_log.submission_id`. Outside any caller transaction the call
 * starts its own short tx. Ordering is provided by the table's monotonically
 * increasing `id` - no manual sequence column, no concurrent-append collisions.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service
class SubmissionLogger(
    private val submissionLogService: SubmissionLogService,
) {

    fun appendOne(submissionId: Long, line: String) = append(submissionId, listOf(line))

    @Transactional(propagation = Propagation.REQUIRED)
    fun append(submissionId: Long, lines: List<String>) {
        if (lines.isEmpty()) return
        val now = LocalDateTime.now()
        val entities = lines.map { line ->
            SubmissionLog().apply {
                this.submissionId = submissionId
                this.line = line
                this.createdAt = now
            }
        }
        submissionLogService.save(entities)
    }
}
