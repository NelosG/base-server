package com.nelos.parallel.gitlab.enums

import com.nelos.parallel.commons.entity.enums.JpaEnum
import com.nelos.parallel.commons.entity.enums.JpaEnumConverter
import jakarta.persistence.Converter

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
enum class SubmissionStatus(
    private val dbKey: String
) : JpaEnum {
    PENDING("PN"),
    DISPATCHED("DS"),
    COMPLETED("OK"),
    FAILED("FL"),
    ERROR("ER"),
    REJECTED("RJ");

    override fun getDbKey() = dbKey

    @Converter(autoApply = true)
    class JpaConverter : JpaEnumConverter<SubmissionStatus>()
}
