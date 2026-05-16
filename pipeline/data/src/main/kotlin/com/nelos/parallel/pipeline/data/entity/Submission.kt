package com.nelos.parallel.pipeline.data.entity

import com.nelos.parallel.commons.entity.AbstractEntity
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Entity(name = Submission.TABLE_NAME)
@Table(name = Submission.TABLE_NAME)
class Submission : AbstractEntity() {

    @Id
    @Column(name = ID)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "${TABLE_NAME}_seq")
    @SequenceGenerator(name = "${TABLE_NAME}_seq", sequenceName = "seq_${TABLE_NAME}", allocationSize = 1)
    override fun getId(): Long? = id

    @get:Column(name = "assignment_id")
    var assignmentId: Long? = null

    @get:Column(name = "user_id")
    var userId: Long? = null

    @get:Column(name = "job_id")
    var jobId: Long? = null

    @get:Column(name = "mr_iid")
    var mrIid: Long? = null

    @get:Column(name = "source_branch")
    var sourceBranch: String? = null

    @get:Column(name = "solution_repo_url")
    var solutionRepoUrl: String? = null

    @get:Column(name = "commit_sha")
    var commitSha: String? = null

    @get:Column(name = "status")
    var status: SubmissionStatus? = SubmissionStatus.PENDING

    @get:Column(name = "created_at")
    var createdAt: LocalDateTime? = null

    @get:Column(name = "completed_at")
    var completedAt: LocalDateTime? = null

    @get:Column(name = "result_summary")
    var resultSummary: String? = null

    companion object {
        const val TABLE_NAME = "prl_submission"
    }
}
