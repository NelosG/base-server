package com.nelos.parallel.pipeline.commons.service

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.nelos.parallel.pipeline.commons.enums.ScriptType

/**
 * Instructor-authored script attached to an assignment that overrides the
 * default test-count verdict. Persisted on `Assignment.properties` as JSON;
 * loaded into [EvaluationContext.script] for the matching
 * [com.nelos.parallel.pipeline.commons.extension.ScriptVerdictExtension] to
 * execute. Only one script per assignment - either KTS or Python, not both.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class EvaluatorScript @JsonCreator constructor(
    @param:JsonProperty("type") val type: ScriptType,
    @param:JsonProperty("source") val source: String,
    @param:JsonProperty("timeoutMs") val timeoutMs: Long = 5000,
    // Persisted with the script so an instructor can park their work without
    // deleting it. When false the evaluator skips the extension chain entirely
    // and the default test-count verdict stands.
    @param:JsonProperty("enabled") val enabled: Boolean = true,
)
