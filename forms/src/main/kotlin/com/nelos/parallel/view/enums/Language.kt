package com.nelos.parallel.view.enums

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * Supported UI languages.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
enum class Language(@JsonValue val key: String, val label: String) {
    EN("en", "English"),
    RU("ru", "Russian");

    companion object {
        @JsonCreator
        @JvmStatic
        fun fromKey(key: String): Language = entries.first { it.key == key }
    }
}
