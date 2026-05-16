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

    override val adaptersInPreferenceOrder: List<NodeAdapter> =
        PREFERENCE_ORDER.mapNotNull { map[it] }

    override fun findAdapter(type: TransportType): NodeAdapter? = map[type]

    companion object {
        /**
         * Domain-level transport preference. AMQP first because Rabbit handles
         * load-balancing across live consumers internally; HTTP is the
         * point-to-point fallback that requires the orchestrator to pick a
         * specific reachable endpoint.
         */
        private val PREFERENCE_ORDER = listOf(TransportType.AMQP, TransportType.HTTP)
    }
}
