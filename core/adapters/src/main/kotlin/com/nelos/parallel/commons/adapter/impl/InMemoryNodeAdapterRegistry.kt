package com.nelos.parallel.commons.adapter.impl

import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.NodeAdapterRegistry
import com.nelos.parallel.commons.adapter.enums.TransportType

/**
 * In-memory implementation of [NodeAdapterRegistry] backed by a map of adapters keyed by transport type.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class InMemoryNodeAdapterRegistry(adapters: List<NodeAdapter>) : NodeAdapterRegistry {

    private val map = adapters.associateBy { it.transportType }

    override fun findAdapter(type: TransportType): NodeAdapter? = map[type]
}
