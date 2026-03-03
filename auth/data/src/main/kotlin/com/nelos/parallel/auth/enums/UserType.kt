package com.nelos.parallel.auth.enums

import com.nelos.parallel.commons.entity.enums.JpaEnum
import com.nelos.parallel.commons.entity.enums.JpaEnumConverter
import com.nelos.parallel.commons.security.AppRole
import jakarta.persistence.Converter

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
enum class UserType(private val dbKey: String, private val roles: List<String>) : JpaEnum {
    USER(
        "US",
        listOf(
            AppRole.ROLE_USER,
        ),
    ),
    ADMIN(
        "AD",
        listOf(
            AppRole.ROLE_ADMIN,
            AppRole.ROLE_USER,
        ),
    );

    override fun getDbKey() = dbKey

    fun getRoles() = roles

    @Converter(autoApply = true)
    class JpaConverter : JpaEnumConverter<UserType>()
}