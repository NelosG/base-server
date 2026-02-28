package com.nelos.parallel.commons.adapter.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Source type for task submission — defines how the runner should fetch source code.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
enum class SourceType {

    GIT,

    LOCAL;

    @JsonValue
    fun toValue(): String = name.lowercase()

    companion object {

        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): SourceType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown source type: $value")
    }
}
