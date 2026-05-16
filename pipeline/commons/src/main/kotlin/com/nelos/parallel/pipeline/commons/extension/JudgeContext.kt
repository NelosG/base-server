package com.nelos.parallel.pipeline.commons.extension

import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import com.nelos.parallel.pipeline.commons.service.SubmissionVerdict

/**
 * What the instructor's KTS / Python script sees when judging a submission.
 *
 * Intentionally narrower than [com.nelos.parallel.pipeline.commons.service.EvaluationContext]:
 * the script gets the test [result] and the [baseline] verdict computed by
 * the default test-count evaluator, but nothing identifying the student
 * (no login, no submissionId, no MR ref). The verdict the script returns is
 * the orchestrator's pass/fail decision for this submission; identities are
 * the orchestrator's concern, not the script's.
 *
 * New fields go here without breaking existing scripts.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
data class JudgeContext(
    val result: TaskResult,
    val baseline: SubmissionVerdict,
)
