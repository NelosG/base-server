package com.nelos.parallel.pipeline.commons.service

import com.nelos.parallel.jobs.enums.JobStatus
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus

/**
 * Outcome of evaluating a single engine
 * [com.nelos.parallel.commons.adapter.vo.response.TaskResult]: derived statuses
 * for the submission and its job, plus a short human-readable summary suitable
 * for `Submission.resultSummary` or the CI job log echo. All fields are
 * derived from the same TaskResult in one pass so downstream code never
 * re-evaluates.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
data class SubmissionVerdict(
    val submissionStatus: SubmissionStatus,
    val jobStatus: JobStatus,
    val summary: String,
    /**
     * Optional human-readable explanation of why this verdict was reached.
     * The default test-count evaluator leaves this null; custom instructor
     * scripts (.kts / .py) fill it when they override the baseline ("rejected:
     * required speedup of 2x not reached").
     */
    val reason: String? = null,
)
