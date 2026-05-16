package com.nelos.parallel.pipeline.commons.service

import com.nelos.parallel.commons.adapter.vo.response.TaskResult

/**
 * Server-side context handed to the evaluator chain. Carries the raw
 * [result] plus everything an extension needs to decide whether (and how) to
 * override the baseline verdict - currently the per-assignment [script].
 *
 * Wider than [com.nelos.parallel.pipeline.commons.extension.JudgeContext]
 * (which is the SCRIPT'S view - only [result] + baseline verdict, no IDs)
 * because extensions running server-side may need configuration the script
 * itself should not see.
 *
 * Future fields go here without breaking the [SubmissionResultEvaluator.evaluate]
 * signature.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
data class EvaluationContext(
    val result: TaskResult,
    val script: EvaluatorScript? = null,
)
