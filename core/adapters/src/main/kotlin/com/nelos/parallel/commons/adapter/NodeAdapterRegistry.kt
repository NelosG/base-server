package com.nelos.parallel.commons.adapter

import com.nelos.parallel.commons.adapter.enums.TransportType

/**
 * Registry for looking up [NodeAdapter] instances by their [TransportType].
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface NodeAdapterRegistry {

    /**
     * Returns the [NodeAdapter] for the given [type], or `null` if none is registered.
     */
    fun findAdapter(type: TransportType): NodeAdapter?

    /**
     * Adapters ordered by transport preference: callers that can pick any
     * working transport (e.g. a dispatcher selecting a runner) should iterate
     * this list. AMQP is listed before HTTP because Rabbit fans the task out
     * to whichever consumer is currently free, whereas HTTP is a
     * point-to-point call to a specific endpoint that might be down.
     *
     * Empty when no adapters are registered.
     */
    val adaptersInPreferenceOrder: List<NodeAdapter>
}
