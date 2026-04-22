package com.nelos.parallel.commons.adapter.impl

import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.NodeAdapterRegistry
import com.nelos.parallel.commons.adapter.enums.TransportType
import org.springframework.stereotype.Component

/**
 * In-memory implementation of [NodeAdapterRegistry] backed by a map of adapters keyed by transport type.
 * Spring auto-collects all `NodeAdapter` beans into the constructor list.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.nodeAdapterRegistry")
class InMemoryNodeAdapterRegistry(adapters: List<NodeAdapter>) : NodeAdapterRegistry {

    private val map = adapters.associateBy { it.transportType }

    override fun findAdapter(type: TransportType): NodeAdapter? = map[type]
}
