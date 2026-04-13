package com.nelos.parallel.jobs.enums

import com.nelos.parallel.commons.entity.enums.JpaEnum
import com.nelos.parallel.commons.entity.enums.JpaEnumConverter
import jakarta.persistence.Converter

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
enum class JobStatus(
    private val dbKey: String,
    private val priority: Int
) : JpaEnum {
    RESTARTING("RS", -3),
    SCHEDULED("SC", -2),
    RUNNING("RN", -1),
    SUCCESS("OK", 0),
    SKIPPED("SK", 1),
    WARN("WR", 2),
    DUPLICATE("DP", 3),
    FAILED("FL", 4),
    ERROR("ER", 5),
    INTERRUPTING("IG", 6),
    INTERRUPTED("ID", 7);

    override fun getDbKey() = dbKey

    /**
     * Returns the worst job status of two (this and other).
     *
     * @param rhs   the job status to be compared with this.
     *
     * @return The worst of two.
     */
    operator fun plus(rhs: JobStatus): JobStatus =
        if (priority >= rhs.priority) this else rhs

    @Converter(autoApply = true)
    class JpaConverter : JpaEnumConverter<JobStatus>()
}
