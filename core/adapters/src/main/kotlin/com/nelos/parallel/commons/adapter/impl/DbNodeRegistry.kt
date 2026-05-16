package com.nelos.parallel.commons.adapter.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.nelos.parallel.commons.adapter.NodeRegistry
import com.nelos.parallel.commons.adapter.entity.Node
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.service.NodeService
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.findHttpConfig
import com.nelos.parallel.commons.adapter.vo.findTransport
import com.nelos.parallel.commons.adapter.vo.response.TransportConfig
import com.nelos.parallel.commons.adapter.vo.response.TransportInfo
import com.nelos.parallel.commons.service.TxAfterCommit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * Database-backed [NodeRegistry] implementation. Replaces the in-memory variant so that
 * registrations survive parallel-server restarts and are visible to all instances behind
 * a load balancer.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Component("prl.nodeRegistry")
class DbNodeRegistry(
    private val nodeService: NodeService,
) : NodeRegistry {

    /**
     * Short-TTL cache for the full node list, keyed by a constant - the registry
     * is small (typically <10 nodes) but `findAll()` is called on every pipeline
     * submit. Cache is invalidated on every local write (register / deregister /
     * updateNode / removeTransport) so locally-visible state is always fresh;
     * the TTL is a safety net for cluster-mate writes that bypass this instance
     * and for [invalidateCache] called by the dispatcher when no cached node
     * was reachable.
     */
    private val listCache: Cache<String, List<NodeInfo>> = Caffeine.newBuilder()
        .maximumSize(1)
        .expireAfterWrite(Duration.ofSeconds(10))
        .build()

    @Transactional(propagation = Propagation.REQUIRED)
    override fun register(node: NodeInfo): NodeInfo {
        evictByEndpoint(node)
        // SELECT ... FOR UPDATE: a concurrent register / removeTransport /
        // updateNode on the same nodeId blocks here until our tx commits,
        // so the merge below sees a stable snapshot and writes can't be
        // lost. First-ever insert (existing == null) is still racy on the
        // unique constraint; that path is rare and the adapter will retry.
        val existing = nodeService.findByNodeIdForUpdate(node.nodeId)
        if (existing != null) {
            val existingAt = existing.registeredAt ?: Instant.EPOCH
            if (node.registeredAt.isBefore(existingAt)) {
                LOG.debug(
                    "Ignoring outdated registration for node {} (existing={}; incoming={})",
                    node.nodeId, existingAt, node.registeredAt,
                )
                return existing.toNodeInfo()
            }
            existing.applyFrom(node)
            val saved = nodeService.save(existing)
            LOG.info("Node re-registered: {} (transports={})", node.nodeId, formatTransports(node))
            scheduleInvalidate()
            return saved.toNodeInfo()
        }
        val entity = Node().apply { applyFrom(node) }
        val saved = nodeService.save(entity)
        LOG.info("Node registered: {} (transports={})", node.nodeId, formatTransports(node))
        scheduleInvalidate()
        return saved.toNodeInfo()
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun deregister(nodeId: String): Boolean {
        val existing = nodeService.findByNodeId(nodeId) ?: run {
            LOG.debug("Node not found for deregistration: {}", nodeId)
            return false
        }
        nodeService.remove(existing)
        LOG.info("Node deregistered: {}", nodeId)
        scheduleInvalidate()
        return true
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findById(nodeId: String): NodeInfo? =
        nodeService.findByNodeId(nodeId)?.toNodeInfo()

    override fun findAll(): List<NodeInfo> =
    // nodeService.findAll() is itself @Transactional(SUPPORTS, readOnly) via GenericServiceImpl,
        // so the cache loader doesn't need its own transactional boundary.
        listCache.get(ALL_KEY) { nodeService.findAll().map { it.toNodeInfo() } }

    override fun findByTransport(transport: TransportType): List<NodeInfo> =
        findAll().filter { it.findTransport(transport) != null }

    override fun invalidateCache() {
        listCache.invalidateAll()
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun removeTransport(nodeId: String, transportType: TransportType): NodeInfo? {
        val entity = nodeService.findByNodeIdForUpdate(nodeId) ?: return null
        val updatedTransports = entity.transports?.filter { it.type != transportType }
        if (updatedTransports.isNullOrEmpty()) {
            nodeService.remove(entity)
            LOG.info("Removed node {} (no transports after removing {})", nodeId, transportType)
            scheduleInvalidate()
            return null
        }
        entity.transports = updatedTransports
        val saved = nodeService.save(entity)
        LOG.info("Removed {} transport from node {} ({} remaining)", transportType, nodeId, updatedTransports.size)
        scheduleInvalidate()
        return saved.toNodeInfo()
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun updateNode(node: NodeInfo): NodeInfo {
        val existing = nodeService.findByNodeIdForUpdate(node.nodeId)
        val entity = existing ?: Node()
        entity.applyFrom(node)
        val saved = nodeService.save(entity).toNodeInfo()
        scheduleInvalidate()
        return saved
    }

    private fun evictByEndpoint(node: NodeInfo) {
        val httpConfig = node.findHttpConfig() ?: return
        val port = httpConfig.port ?: return
        if (port <= 0) return
        val host = httpConfig.host ?: return

        val stale = nodeService.findAll()
            .map { it.toNodeInfo() }
            .firstOrNull { existing ->
                if (existing.nodeId == node.nodeId) return@firstOrNull false
                val existingHttp = existing.findHttpConfig() ?: return@firstOrNull false
                existingHttp.host == host && existingHttp.port == port
            } ?: return

        val staleEntity = nodeService.findByNodeId(stale.nodeId) ?: return
        nodeService.remove(staleEntity)
        LOG.info("Evicted stale node {} (same endpoint {}:{})", stale.nodeId, host, port)
        scheduleInvalidate()
    }

    /**
     * Invalidate the in-process cache after the current transaction commits.
     * Outside a transaction (e.g. dispatcher-driven `invalidateCache()` call from
     * PipelineService) runs the action immediately - see [TxAfterCommit].
     */
    private fun scheduleInvalidate() {
        TxAfterCommit.runAfterCommit { invalidateCache() }
    }

    /**
     * Merge an incoming [NodeInfo] into this entity. Null fields on the incoming
     * side are NOT propagated - they preserve whatever the entity already has.
     * Transports merge per [TransportType]: incoming entries override same-type
     * entries already on the entity, other types are kept. Explicit transport
     * removal goes through [removeTransport].
     */
    private fun Node.applyFrom(node: NodeInfo) {
        nodeId = node.nodeId
        capabilities = node.capabilities ?: capabilities
        transports = mergeTransports(transports, node.transports)
        resourceProviders = node.resourceProviders ?: resourceProviders
        registeredAt = node.registeredAt
    }

    private fun mergeTransports(
        existing: List<TransportInfo>?,
        incoming: List<TransportInfo>?,
    ): List<TransportInfo>? {
        if (incoming.isNullOrEmpty()) return existing
        val byType = (existing ?: emptyList()).associateBy { it.type }.toMutableMap()
        incoming.forEach { byType[it.type] = it }
        return byType.values.toList()
    }

    private fun Node.toNodeInfo(): NodeInfo = NodeInfo(
        nodeId = nodeId ?: error("Node has no nodeId"),
        capabilities = capabilities,
        transports = transports,
        resourceProviders = resourceProviders,
        registeredAt = registeredAt ?: Instant.now(),
    )

    private fun formatTransports(node: NodeInfo): String =
        node.transports?.joinToString { t ->
            val endpoint = (t.config as? TransportConfig.HttpConfig)?.let { c ->
                " ${c.host ?: "?"}:${c.port ?: "?"}"
            } ?: ""
            "${t.type.toValue()}$endpoint"
        } ?: "none"

    companion object {
        private val LOG = LoggerFactory.getLogger(DbNodeRegistry::class.java)
        private const val ALL_KEY = "all"
    }
}
