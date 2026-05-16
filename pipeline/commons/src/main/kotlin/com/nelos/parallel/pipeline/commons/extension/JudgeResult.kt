package com.nelos.parallel.pipeline.commons.extension

/**
 * What an instructor script returns. Translated by the
 * [ScriptVerdictExtension] into a [com.nelos.parallel.pipeline.commons.service.SubmissionVerdict]:
 * `pass=true` maps to `COMPLETED`, `pass=false` to `FAILED`. The optional
 * [summary] replaces the baseline one-liner; [reason] is the long
 * explanation surfaced in the UI badge.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
data class JudgeResult(
    val pass: Boolean,
    val summary: String? = null,
    val reason: String? = null,
)
