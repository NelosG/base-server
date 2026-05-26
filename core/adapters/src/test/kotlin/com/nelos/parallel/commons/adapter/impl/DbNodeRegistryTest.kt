package com.nelos.parallel.commons.adapter.impl

import com.nelos.parallel.commons.adapter.entity.Node
import com.nelos.parallel.commons.adapter.enums.AdapterStatus
import com.nelos.parallel.commons.adapter.enums.TransportType
import com.nelos.parallel.commons.adapter.service.NodeService
import com.nelos.parallel.commons.adapter.vo.NodeInfo
import com.nelos.parallel.commons.adapter.vo.response.TransportConfig
import com.nelos.parallel.commons.adapter.vo.response.TransportInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Drives [DbNodeRegistry]'s merge / eviction / race-guard logic against a
 * mocked [NodeService]. The DAO itself is JPA-backed and tested at integration
 * level; here we only exercise behaviour the registry owns.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class DbNodeRegistryTest {

    private val nodeService: NodeService = mock()
    private lateinit var registry: DbNodeRegistry

    @BeforeEach
    fun setUp() {
        registry = DbNodeRegistry(nodeService)
    }

    // ---- helpers ------------------------------------------------------------

    private fun httpTransport(
        host: String? = "127.0.0.1",
        port: Int? = 8080,
        token: String? = null,
        status: AdapterStatus = AdapterStatus.RUNNING,
    ) = TransportInfo(
        type = TransportType.HTTP,
        status = status,
        config = TransportConfig.HttpConfig(host = host, port = port, authToken = token),
    )

    private fun amqpTransport(host: String? = "rabbit", port: Int? = 5672) = TransportInfo(
        type = TransportType.AMQP,
        status = AdapterStatus.RUNNING,
        config = TransportConfig.AmqpConfig(host = host, port = port),
    )

    private fun nodeEntity(
        id: String,
        transports: List<TransportInfo> = listOf(httpTransport()),
        registeredAt: Instant = Instant.now(),
    ): Node = Node().apply {
        nodeId = id
        this.transports = transports
        this.registeredAt = registeredAt
    }

    private fun nodeInfo(
        id: String,
        transports: List<TransportInfo>? = listOf(httpTransport()),
        registeredAt: Instant = Instant.now(),
    ): NodeInfo = NodeInfo(nodeId = id, transports = transports, registeredAt = registeredAt)

    // ---- register / re-register --------------------------------------------

    @Test
    fun `first-ever register inserts a fresh entity carrying the incoming fields`() {
        whenever(nodeService.findAll()).thenReturn(emptyList())
        whenever(nodeService.findByNodeIdForUpdate("n1")).thenReturn(null)
        whenever(nodeService.save(any<Node>())).thenAnswer { it.arguments[0] as Node }

        val saved = registry.register(nodeInfo("n1"))

        assertEquals("n1", saved.nodeId)
        verify(nodeService).save(any<Node>())
    }

    @Test
    fun `re-register with same nodeId merges into the existing entity`() {
        val existingAt = Instant.parse("2026-01-01T00:00:00Z")
        val existing = nodeEntity("n1", listOf(httpTransport(token = "old-token")), existingAt)
        whenever(nodeService.findAll()).thenReturn(emptyList())
        whenever(nodeService.findByNodeIdForUpdate("n1")).thenReturn(existing)
        whenever(nodeService.save(any<Node>())).thenAnswer { it.arguments[0] as Node }

        val incoming = nodeInfo(
            "n1",
            transports = listOf(httpTransport(token = null)), // re-send without token
            registeredAt = existingAt.plusSeconds(60),
        )
        val saved = registry.register(incoming)

        // authToken is preserved through the merge - the re-registration didn't
        // re-send it, but it must survive.
        val httpConfig = saved.transports?.first()?.config as TransportConfig.HttpConfig
        assertEquals("old-token", httpConfig.authToken)
    }

    @Test
    fun `outdated registration timestamp returns the existing entity without saving`() {
        val existingAt = Instant.parse("2026-01-01T00:00:00Z")
        val existing = nodeEntity("n1", registeredAt = existingAt)
        whenever(nodeService.findAll()).thenReturn(emptyList())
        whenever(nodeService.findByNodeIdForUpdate("n1")).thenReturn(existing)

        val incoming = nodeInfo("n1", registeredAt = existingAt.minusSeconds(60))
        val result = registry.register(incoming)

        assertEquals("n1", result.nodeId)
        verify(nodeService, never()).save(any<Node>())
    }

    @Test
    fun `registration evicts a stale node sharing the same HTTP endpoint`() {
        // Old-pod scenario: container restart reuses the host:port but presents a
        // fresh nodeId. The old row must go so the registry stays unique on
        // endpoint.
        val stale = nodeInfo("old-nodeId", listOf(httpTransport(host = "10.0.0.1", port = 9000)))
        val staleEntity = nodeEntity("old-nodeId", listOf(httpTransport(host = "10.0.0.1", port = 9000)))
        whenever(nodeService.findAll()).thenReturn(listOf(staleEntity))
        whenever(nodeService.findByNodeIdForUpdate("old-nodeId")).thenReturn(staleEntity)
        whenever(nodeService.findByNodeIdForUpdate("new-nodeId")).thenReturn(null)
        whenever(nodeService.save(any<Node>())).thenAnswer { it.arguments[0] as Node }

        val incoming = nodeInfo("new-nodeId", listOf(httpTransport(host = "10.0.0.1", port = 9000)))
        registry.register(incoming)

        verify(nodeService).remove(staleEntity)
        // And the new one gets saved.
        verify(nodeService).save(any<Node>())
    }

    @Test
    fun `registration without HTTP transport does not trigger endpoint eviction`() {
        whenever(nodeService.findByNodeIdForUpdate("n1")).thenReturn(null)
        whenever(nodeService.save(any<Node>())).thenAnswer { it.arguments[0] as Node }

        registry.register(nodeInfo("n1", transports = listOf(amqpTransport())))

        verify(nodeService, never()).findAll() // never scanned for endpoint match
        verify(nodeService, never()).remove(any<Node>())
    }

    @Test
    fun `registration with same nodeId at same endpoint does not evict itself`() {
        // The eviction loop must skip rows whose nodeId equals the incoming one.
        val same = nodeEntity("n1", listOf(httpTransport(host = "h", port = 81)))
        whenever(nodeService.findAll()).thenReturn(listOf(same))
        whenever(nodeService.findByNodeIdForUpdate("n1")).thenReturn(same)
        whenever(nodeService.save(any<Node>())).thenAnswer { it.arguments[0] as Node }

        registry.register(nodeInfo("n1", listOf(httpTransport(host = "h", port = 81))))

        verify(nodeService, never()).remove(any<Node>())
    }

    // ---- deregister --------------------------------------------------------

    @Test
    fun `deregister returns false when the node is not in the registry`() {
        whenever(nodeService.findByNodeIdForUpdate("ghost")).thenReturn(null)

        assertFalse(registry.deregister("ghost"))
        verify(nodeService, never()).remove(any<Node>())
    }

    @Test
    fun `deregister removes the entity and returns true`() {
        val existing = nodeEntity("n1")
        whenever(nodeService.findByNodeIdForUpdate("n1")).thenReturn(existing)

        assertTrue(registry.deregister("n1"))
        verify(nodeService).remove(existing)
    }

    // ---- findAll / cache invalidation --------------------------------------

    @Test
    fun `findAll caches the result - second call doesn't hit the DAO`() {
        val n = nodeEntity("n1")
        whenever(nodeService.findAll()).thenReturn(listOf(n))

        val first = registry.findAll()
        val second = registry.findAll()

        assertEquals(1, first.size)
        assertEquals(1, second.size)
        verify(nodeService, times(1)).findAll()
    }

    @Test
    fun `register invalidates the findAll cache so subsequent reads see the write`() {
        val n = nodeEntity("n1")
        whenever(nodeService.findAll()).thenReturn(listOf(n))
        whenever(nodeService.findByNodeIdForUpdate(any())).thenReturn(null)
        whenever(nodeService.save(any<Node>())).thenAnswer { it.arguments[0] as Node }

        registry.findAll() // populate cache
        registry.register(nodeInfo("n2", transports = listOf(amqpTransport()))) // invalidates
        registry.findAll()

        verify(nodeService, times(2)).findAll() // once before register, once after
    }

    @Test
    fun `invalidateCache forces the next findAll to hit the DAO again`() {
        val n = nodeEntity("n1")
        whenever(nodeService.findAll()).thenReturn(listOf(n))

        registry.findAll()
        registry.invalidateCache()
        registry.findAll()

        verify(nodeService, times(2)).findAll()
    }

    // ---- findByTransport ---------------------------------------------------

    @Test
    fun `findByTransport returns only nodes carrying the requested transport`() {
        val httpOnly = nodeEntity("a", listOf(httpTransport()))
        val amqpOnly = nodeEntity("b", listOf(amqpTransport()))
        val both = nodeEntity("c", listOf(httpTransport(), amqpTransport()))
        whenever(nodeService.findAll()).thenReturn(listOf(httpOnly, amqpOnly, both))

        val http = registry.findByTransport(TransportType.HTTP)
        val amqp = registry.findByTransport(TransportType.AMQP)

        assertEquals(setOf("a", "c"), http.map { it.nodeId }.toSet())
        assertEquals(setOf("b", "c"), amqp.map { it.nodeId }.toSet())
    }

    // ---- findById ----------------------------------------------------------

    @Test
    fun `findById returns null when no row exists`() {
        whenever(nodeService.findByNodeId("ghost")).thenReturn(null)
        assertNull(registry.findById("ghost"))
    }

    @Test
    fun `findById returns the matching NodeInfo`() {
        val entity = nodeEntity("n1")
        whenever(nodeService.findByNodeId("n1")).thenReturn(entity)
        assertEquals("n1", registry.findById("n1")?.nodeId)
    }

    // ---- removeTransport ---------------------------------------------------

    @Test
    fun `removeTransport returns null and deletes the row when no transports remain`() {
        val onlyHttp = nodeEntity("n1", listOf(httpTransport()))
        whenever(nodeService.findByNodeIdForUpdate("n1")).thenReturn(onlyHttp)

        val remaining = registry.removeTransport("n1", TransportType.HTTP)

        assertNull(remaining)
        verify(nodeService).remove(onlyHttp)
    }

    @Test
    fun `removeTransport keeps the node when other transports remain`() {
        val multi = nodeEntity("n1", listOf(httpTransport(), amqpTransport()))
        whenever(nodeService.findByNodeIdForUpdate("n1")).thenReturn(multi)
        whenever(nodeService.save(any<Node>())).thenAnswer { it.arguments[0] as Node }

        val remaining = registry.removeTransport("n1", TransportType.HTTP)

        assertNotNull(remaining)
        assertEquals(1, remaining.transports?.size)
        assertEquals(TransportType.AMQP, remaining.transports?.first()?.type)
        verify(nodeService, never()).remove(any<Node>())
    }

    @Test
    fun `removeTransport on a missing node is a no-op returning null`() {
        whenever(nodeService.findByNodeIdForUpdate("ghost")).thenReturn(null)

        assertNull(registry.removeTransport("ghost", TransportType.HTTP))
        verify(nodeService, never()).save(any<Node>())
    }

    // ---- updateNode --------------------------------------------------------

    @Test
    fun `updateNode falls back to register when the row vanished between snapshot and lock`() {
        whenever(nodeService.findByNodeIdForUpdate("n1")).thenReturn(null)
        whenever(nodeService.findAll()).thenReturn(emptyList())
        whenever(nodeService.save(any<Node>())).thenAnswer { it.arguments[0] as Node }

        // updateNode -> findByNodeIdForUpdate returns null -> register path:
        // register also calls findByNodeIdForUpdate (still null) -> save.
        registry.updateNode(nodeInfo("n1", transports = listOf(amqpTransport())))

        verify(nodeService).save(any<Node>())
    }

    @Test
    fun `updateNode merges incoming into the existing row`() {
        val existing = nodeEntity("n1", listOf(httpTransport(token = "tok")))
        whenever(nodeService.findByNodeIdForUpdate("n1")).thenReturn(existing)
        whenever(nodeService.save(any<Node>())).thenAnswer { it.arguments[0] as Node }

        // Re-sending only the AMQP transport must NOT wipe the HTTP one - merge
        // keeps the other types.
        val updated = registry.updateNode(nodeInfo("n1", transports = listOf(amqpTransport())))

        val byType = updated.transports?.associateBy { it.type }
        assertNotNull(byType?.get(TransportType.HTTP))
        assertNotNull(byType?.get(TransportType.AMQP))
    }

    // ---- transport / config merge ------------------------------------------

    @Test
    fun `re-register with HTTP omitting authToken does not erase the previously stored token`() {
        // This is the core "merge incoming on top of existing" guarantee.
        val existing = nodeEntity("n1", listOf(httpTransport(token = "stored")), Instant.parse("2026-01-01T00:00:00Z"))
        whenever(nodeService.findByNodeIdForUpdate("n1")).thenReturn(existing)
        whenever(nodeService.findAll()).thenReturn(emptyList())
        whenever(nodeService.save(any<Node>())).thenAnswer { it.arguments[0] as Node }

        val incoming = nodeInfo(
            "n1",
            transports = listOf(httpTransport(token = null)),
            registeredAt = Instant.parse("2026-01-02T00:00:00Z"),
        )
        val saved = registry.register(incoming)

        val http = saved.transports?.first()?.config as TransportConfig.HttpConfig
        assertEquals("stored", http.authToken)
    }

    @Test
    fun `re-register with HTTP overriding authToken replaces the previously stored token`() {
        val existing = nodeEntity("n1", listOf(httpTransport(token = "stored")))
        whenever(nodeService.findByNodeIdForUpdate("n1")).thenReturn(existing)
        whenever(nodeService.findAll()).thenReturn(emptyList())
        whenever(nodeService.save(any<Node>())).thenAnswer { it.arguments[0] as Node }

        val saved = registry.register(
            nodeInfo("n1", transports = listOf(httpTransport(token = "rotated"))),
        )

        val http = saved.transports?.first()?.config as TransportConfig.HttpConfig
        assertEquals("rotated", http.authToken)
    }
}
