package com.nelos.parallel.commons.adapter.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Status of an adapter or resource provider on a test-runner node.
 * Values correspond to adapter_status.h in C-tests-runner.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
enum class AdapterStatus(private val key: String) {

    AVAILABLE("available"),
    RUNNING("running"),
    STARTED("started"),
    STOPPED("stopped"),
    FAILED("failed");

    @JsonValue
    fun toValue(): String = key

    companion object {

        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): AdapterStatus =
            entries.firstOrNull { it.key.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown adapter status: $value")
    }
}
