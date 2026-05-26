package com.nelos.parallel.pipeline.commons.extension

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * What an instructor script returns. Translated by the
 * [ScriptVerdictExtension] into a [com.nelos.parallel.pipeline.commons.service.SubmissionVerdict]:
 * `pass=true` maps to `COMPLETED`, `pass=false` to `FAILED`. The optional
 * [summary] replaces the baseline one-liner; [reason] is the long
 * explanation surfaced in the UI badge.
 *
 * The `@JsonCreator` constructor is what [com.nelos.parallel.pipeline.python.PythonVerdictExtension]
 * deserialises script stdout into - without it Jackson would have no
 * constructor to call (Kotlin data classes generate only the all-args one,
 * which Jackson can't see without `jackson-module-kotlin`).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class JudgeResult @JsonCreator constructor(
    @param:JsonProperty("pass") val pass: Boolean,
    @param:JsonProperty("summary") val summary: String? = null,
    @param:JsonProperty("reason") val reason: String? = null,
)
