package com.nelos.parallel.commons.adapter.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Supported transport protocols for node communication.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
enum class TransportType {

    HTTP,

    AMQP;

    @JsonValue
    fun toValue(): String = name.lowercase()

    companion object {

        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): TransportType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown transport type: $value")
    }
}