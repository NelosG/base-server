package com.nelos.parallel.view.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.nelos.parallel.view.enums.Language
import com.nelos.parallel.view.enums.Theme

/**
 * Value object representing the user's settings state.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class SettingsView @JsonCreator constructor(
    @param:JsonProperty("theme") val theme: Theme? = null,
    @param:JsonProperty("language") val language: Language? = null,
    @param:JsonProperty("notificationsEnabled") val notificationsEnabled: Boolean? = null,
)