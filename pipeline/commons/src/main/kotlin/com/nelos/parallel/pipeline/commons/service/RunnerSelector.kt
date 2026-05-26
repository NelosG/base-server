package com.nelos.parallel.pipeline.commons.service

import com.nelos.parallel.commons.adapter.enums.TransportType

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

    /**
     * Same as [selectRunner], but constrained to nodes that own the requested
     * transport. Used by per-transport [com.nelos.parallel.pipeline.runner.service.TaskRunner]
     * implementations to pick a node without crossing into other transports.
     */
    fun selectRunner(submissionId: Long, transport: TransportType): SelectedRunner?
}
