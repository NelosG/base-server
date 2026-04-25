package com.nelos.parallel.adapters.rabbit

import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.vo.NodeInfo

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface RabbitNodeAdapter : NodeAdapter {

    /**
     * Discovers live AMQP runner nodes on demand by broadcasting `statusRequest` to
     * the [RabbitConstants.NODE_FANOUT_EXCHANGE] and aggregating replies that arrive
     * within [timeoutMs] milliseconds. Returns an empty list if no replies are received.
     */
    fun discoverNodes(timeoutMs: Long = 2000): List<NodeInfo>
}