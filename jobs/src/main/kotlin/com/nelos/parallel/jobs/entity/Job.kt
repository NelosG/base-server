package com.nelos.parallel.jobs.entity

import com.nelos.parallel.commons.entity.AbstractEntity
import com.nelos.parallel.jobs.enums.JobStatus
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Entity(name = Job.TABLE_NAME)
@Table(name = Job.TABLE_NAME)
class Job : AbstractEntity() {

    @Id
    @Column(name = ID)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "${TABLE_NAME}_seq")
    @SequenceGenerator(name = "${TABLE_NAME}_seq", sequenceName = "seq_${TABLE_NAME}", allocationSize = 1)
    override fun getId(): Long? = id

    @get:Column(name = "start_date")
    var startDate: LocalDateTime? = null

    @get:Column(name = "end_date")
    var endDate: LocalDateTime? = null

    @get:Column(name = "total_count")
    var totalCount: Int? = null

    @get:Column(name = "processed_count")
    var processedCount: Int? = null

    @get:Column(name = "status")
    var status: JobStatus? = null

    companion object {
        const val TABLE_NAME = "prl_job"
    }
}
