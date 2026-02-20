package com.nelos.parallel.commons.adapter.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Event type for node lifecycle events.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
enum class NodeEventType(private val key: String) {

    ONLINE("online"),
    OFFLINE("offline"),
    INFO("info");

    @JsonValue
    fun toValue(): String = key

    companion object {

        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): NodeEventType =
            entries.firstOrNull { it.key.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown node event type: $value")
    }
}
