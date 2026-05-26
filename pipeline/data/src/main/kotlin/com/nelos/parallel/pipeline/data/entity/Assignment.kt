package com.nelos.parallel.pipeline.data.entity

import com.nelos.parallel.commons.entity.CodeEntity
import com.nelos.parallel.pipeline.commons.service.EvaluatorScript
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Entity(name = Assignment.TABLE_NAME)
@Table(name = Assignment.TABLE_NAME)
class Assignment : CodeEntity() {

    @Id
    @Column(name = ID)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "${TABLE_NAME}_seq")
    @SequenceGenerator(name = "${TABLE_NAME}_seq", sequenceName = "seq_${TABLE_NAME}", allocationSize = 1)
    override fun getId(): Long? = id

    @get:Column(name = "gitlab_project_path")
    var gitlabProjectPath: String? = null

    @get:Column(name = "test_repo_url")
    var testRepoUrl: String? = null

    @get:Column(name = "test_repo_branch")
    var testRepoBranch: String? = "main"

    @get:Column(name = "memory_limit_mb")
    var memoryLimitMb: Long? = null

    @get:Column(name = "threads")
    var threads: Int? = null

    @get:Column(name = "wall_time_sec")
    var wallTimeSec: Int? = null

    @get:Column(name = "cpu_time_sec")
    var cpuTimeSec: Int? = null

    @get:Column(name = "max_processes")
    var maxProcesses: Int? = null

    @get:Column(name = "warmup_iterations")
    var warmupIterations: Int? = null

    @get:Column(name = "active")
    var active: Boolean = true

    /**
     * Instructor-authored script that overrides the default test-count verdict.
     * `null` => use the default evaluator. Stored as JSON; the matching
     * `ScriptVerdictExtension` (KTS / Python) executes it at evaluation time.
     */
    @get:Column(name = "evaluator_script")
    @get:JdbcTypeCode(SqlTypes.JSON)
    var evaluatorScript: EvaluatorScript? = null

    companion object {
        const val TABLE_NAME = "prl_assignment"
    }
}
