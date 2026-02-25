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
}
