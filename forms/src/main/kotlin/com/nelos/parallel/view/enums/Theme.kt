package com.nelos.parallel.view.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
enum class Theme(@JsonValue val key: String, val label: String) {
    DARK("dark", "Dark"),
    LIGHT("light", "Light");

    companion object {
        @JsonCreator
        @JvmStatic
        fun fromKey(key: String): Theme = entries.first { it.key == key }
    }
}
