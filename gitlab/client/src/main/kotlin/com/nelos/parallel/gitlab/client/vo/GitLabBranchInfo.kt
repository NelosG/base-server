package com.nelos.parallel.gitlab.client.vo

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
class GitLabBranchInfo @JsonCreator constructor(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("default") val default_: Boolean? = null,
)
