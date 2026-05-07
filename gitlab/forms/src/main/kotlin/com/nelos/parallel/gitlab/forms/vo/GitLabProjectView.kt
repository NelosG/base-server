package com.nelos.parallel.gitlab.forms.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class GitLabProjectView @JsonCreator constructor(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("pathWithNamespace") val pathWithNamespace: String? = null,
    @param:JsonProperty("gitHttpUrl") val gitHttpUrl: String? = null,
)
