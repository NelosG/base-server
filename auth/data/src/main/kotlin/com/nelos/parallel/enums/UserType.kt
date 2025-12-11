package com.nelos.parallel.enums

import com.nelos.parallel.core.entity.enums.JpaEnum
import com.nelos.parallel.core.entity.enums.JpaEnumConverter
import jakarta.persistence.Converter

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
enum class UserType(private val dbKey: String, private val roles: List<String>) : JpaEnum {
    USER(
        "US",
        listOf(
            "ROLE_USER",
        ),
    ),
    ADMIN(
        "AD",
        listOf(
            "ROLE_ADMIN",
            "ROLE_USER",
        ),
    );

    override fun getDbKey() = dbKey

    fun getRoles() = roles

    @Converter(autoApply = true)
    class JpaConverter : JpaEnumConverter<UserType>()
}