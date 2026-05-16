package com.nelos.parallel.commons.adapter

import com.nelos.parallel.commons.adapter.vo.NodeInfo

/**
 * Result of [NodeAdapter.pickRunnerNode] - a transport-specific decision about
 * which node (if any) should receive the next dispatch over this transport.
 *
 * - [live] is the chosen node, or `null` if none of the candidates were usable.
 * - [deadNodes] lists `nodeId`s the adapter probed and found unreachable. The
 *   caller strips those nodes' matching transport from the registry so future
 *   dispatches skip them.
 *
 * Broker-routed transports (AMQP) typically pick a candidate without probing
 * and return an empty `deadNodes`. Point-to-point transports (HTTP) probe each
 * candidate until one responds.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
data class RunnerPick(
    val live: NodeInfo? = null,
    val deadNodes: List<String> = emptyList(),
)
