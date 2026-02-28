package com.nelos.parallel.commons.adapter.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Test mode for task submission.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
enum class TestMode {

    CORRECTNESS,
    PERFORMANCE,
    ALL;

    @JsonValue
    fun toValue(): String = name.lowercase()

    companion object {

        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): TestMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown test mode: $value")
    }
}
