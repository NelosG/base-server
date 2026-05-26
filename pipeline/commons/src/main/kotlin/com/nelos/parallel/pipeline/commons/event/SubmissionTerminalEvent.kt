package com.nelos.parallel.pipeline.commons.event

import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus

/**
 * Fired synchronously at the tail of PipelineServiceImpl.handleResult once the
 * submission has reached a terminal status and the result is persisted. Carries
 * no payload beyond identity and status: listeners that need the full TaskResult
 * read it from prl_submission_result themselves.
 *
 * Production has no listener for this event - publishing is effectively free.
 * Load tests register a listener to capture e2e latency without polling.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
data class SubmissionTerminalEvent(
    val submissionId: Long,
    val status: SubmissionStatus,
)
