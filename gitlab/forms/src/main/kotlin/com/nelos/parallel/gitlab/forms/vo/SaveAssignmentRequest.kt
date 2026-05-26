package com.nelos.parallel.gitlab.forms.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.nelos.parallel.pipeline.commons.service.EvaluatorScript

/**
 * Full-replace payload for `saveAssignment`. PUT semantics: every field on
 * the assignment is set from this request; absence in the JSON simply means
 * the optional fields stay at their default of `null` (i.e. "use engine
 * default" for resource limits, "no script" for `evaluatorScript`).
 *
 * Partial updates that only touch one flag (e.g. enable/disable) go through
 * dedicated single-purpose methods on the view service - this keeps the
 * "null = clear" rule unambiguous for the resource-limit inputs.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class SaveAssignmentRequest @JsonCreator constructor(
    @param:JsonProperty("id") val id: Long? = null,
    @param:JsonProperty("code") val code: String,
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("gitlabProjectPath") val gitlabProjectPath: String,
    @param:JsonProperty("testRepoUrl") val testRepoUrl: String,
    @param:JsonProperty("testRepoBranch") val testRepoBranch: String,
    @param:JsonProperty("description") val description: String? = null,
    @param:JsonProperty("memoryLimitMb") val memoryLimitMb: Long? = null,
    @param:JsonProperty("threads") val threads: Int? = null,
    @param:JsonProperty("wallTimeSec") val wallTimeSec: Int? = null,
    @param:JsonProperty("cpuTimeSec") val cpuTimeSec: Int? = null,
    @param:JsonProperty("maxProcesses") val maxProcesses: Int? = null,
    @param:JsonProperty("warmupIterations") val warmupIterations: Int? = null,
    @param:JsonProperty("active") val active: Boolean = true,
    /**
     * Verdict script payload. `null` means "no script" (clears any existing
     * one). To attach / replace, send the [EvaluatorScript] object; the UI's
     * NONE selection translates to `null` on the wire.
     */
    @param:JsonProperty("evaluatorScript") val evaluatorScript: EvaluatorScript? = null,
)
