package com.nelos.parallel.pipeline.commons.service

import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.vo.NodeInfo

/**
 * A node + the adapter to use against it + the transport type that won.
 * Returned by [RunnerSelector] so callers don't have to juggle three
 * separate values or a positional Triple.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
data class SelectedRunner(
    val node: NodeInfo,
    val adapter: NodeAdapter,
    val transport: TransportType,
)
