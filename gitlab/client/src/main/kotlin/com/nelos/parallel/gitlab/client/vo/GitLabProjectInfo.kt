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
class GitLabProjectInfo @JsonCreator constructor(
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("path_with_namespace") val pathWithNamespace: String? = null,
    @param:JsonProperty("web_url") val webUrl: String? = null,
    @param:JsonProperty("http_url_to_repo") val gitHttpUrl: String? = null,
    @param:JsonProperty("forked_from_project") val forkedFromProject: GitLabProjectInfo? = null,
    @param:JsonProperty("marked_for_deletion_at") val markedForDeletionAt: String? = null,
)
