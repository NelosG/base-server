package com.nelos.parallel.pipeline.data.entity

import com.nelos.parallel.commons.entity.AbstractEntity
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Persistent log + serialized engine result for a finished [Submission].
 * Created in `pipeline.handleResult` once the engine has answered.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Entity(name = SubmissionResult.TABLE_NAME)
@Table(name = SubmissionResult.TABLE_NAME)
class SubmissionResult : AbstractEntity() {

    @Id
    @Column(name = ID)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "${TABLE_NAME}_seq")
    @SequenceGenerator(name = "${TABLE_NAME}_seq", sequenceName = "seq_${TABLE_NAME}", allocationSize = 1)
    override fun getId(): Long? = id

    @get:Column(name = "submission_id")
    var submissionId: Long? = null

    @get:Column(name = "log_text")
    var logText: String? = null

    @get:Column(name = "result_json")
    var resultJson: String? = null

    @get:Column(name = "created_at")
    var createdAt: LocalDateTime? = null

    companion object {
        const val TABLE_NAME = "prl_submission_result"
    }
}
