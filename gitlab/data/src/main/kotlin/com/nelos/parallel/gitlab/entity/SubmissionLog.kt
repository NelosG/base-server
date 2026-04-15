package com.nelos.parallel.gitlab.entity

import com.nelos.parallel.commons.entity.AbstractEntity
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Single line of incremental log output for a [Submission]. Ordering relies on
 * the table's monotonically increasing `id` (filled from `seq_prl_submission_log`),
 * so concurrent appenders cannot collide.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Entity(name = SubmissionLog.TABLE_NAME)
@Table(name = SubmissionLog.TABLE_NAME)
class SubmissionLog : AbstractEntity() {

    @Id
    @Column(name = ID)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "${TABLE_NAME}_seq")
    @SequenceGenerator(name = "${TABLE_NAME}_seq", sequenceName = "seq_${TABLE_NAME}", allocationSize = 1)
    override fun getId(): Long? = id

    @get:Column(name = "submission_id")
    var submissionId: Long? = null

    @get:Column(name = "line")
    var line: String? = null

    @get:Column(name = "created_at")
    var createdAt: LocalDateTime? = null

    companion object {
        const val TABLE_NAME = "prl_submission_log"
    }
}
