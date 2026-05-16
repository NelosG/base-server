package com.nelos.parallel.pipeline.commons.service

import com.nelos.parallel.commons.adapter.vo.response.TaskResult

/**
 * Maps a raw engine [TaskResult] into a verdict the orchestrator can persist
 * and hand back to downstream consumers (CI, instructor UI, student UI).
 *
 * The default implementation derives the verdict from engine status + test
 * counts. Custom per-assignment scripts (.kts / .py) plug in as
 * [com.nelos.parallel.pipeline.commons.extension.VerdictExtension] beans -
 * Spring picks them up and the evaluator folds them over the baseline.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface SubmissionResultEvaluator {

    fun evaluate(context: EvaluationContext): SubmissionVerdict
}
