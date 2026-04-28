package com.nelos.parallel.auth.entity.properties

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * JSON-serialized auxiliary data for [com.nelos.parallel.auth.entity.User], stored in the
 * `properties` jsonb column. Holds transient/optional fields that don't justify dedicated columns.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class UserProperties @JsonCreator constructor(
    @param:JsonProperty("initialPassword") var initialPassword: String? = null,
    @param:JsonProperty("passwordChangeRequired") var passwordChangeRequired: Boolean? = null,
)
