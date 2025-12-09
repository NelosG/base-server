package com.nelos.parallel.vo

import com.nelos.parallel.enums.UserType

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JvmRecord
data class SignUpDto(
    val login: String,
    val password: String,
    val type: UserType,
)