package com.nelos.parallel.pipeline.commons.service

/**
 * Picks a live engine node to dispatch a submission to, choosing between
 * the available transports (AMQP preferred over HTTP) and refreshing the
 * registry when no live node is found in the cached snapshot.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface RunnerSelector {

    fun selectRunner(submissionId: Long): SelectedRunner?
}
