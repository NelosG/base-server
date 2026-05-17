package com.nelos.parallel.commons.adapter.impl

import com.nelos.parallel.commons.adapter.NodeAdapter
import com.nelos.parallel.commons.adapter.enums.TransportType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class InMemoryNodeAdapterRegistryTest {

    private fun adapter(t: TransportType): NodeAdapter = mock { on { transportType } doReturn t }

    @Test
    fun `AMQP is preferred over HTTP regardless of constructor argument order`() {
        val http = adapter(TransportType.HTTP)
        val amqp = adapter(TransportType.AMQP)
        // Pass HTTP first to make sure preference order is NOT the registration order.
        val registry = InMemoryNodeAdapterRegistry(listOf(http, amqp))

        val order = registry.adaptersInPreferenceOrder

        assertEquals(listOf(amqp, http), order)
    }

    @Test
    fun `adaptersInPreferenceOrder skips transports without a registered adapter`() {
        val http = adapter(TransportType.HTTP)
        val registry = InMemoryNodeAdapterRegistry(listOf(http))

        assertEquals(listOf(http), registry.adaptersInPreferenceOrder)
    }

    @Test
    fun `findAdapter returns the adapter for its transport`() {
        val http = adapter(TransportType.HTTP)
        val amqp = adapter(TransportType.AMQP)
        val registry = InMemoryNodeAdapterRegistry(listOf(http, amqp))

        assertSame(http, registry.findAdapter(TransportType.HTTP))
        assertSame(amqp, registry.findAdapter(TransportType.AMQP))
    }

    @Test
    fun `empty registry behaves sensibly`() {
        val registry = InMemoryNodeAdapterRegistry(emptyList())

        assertTrue(registry.adaptersInPreferenceOrder.isEmpty())
        assertNull(registry.findAdapter(TransportType.HTTP))
        assertNull(registry.findAdapter(TransportType.AMQP))
    }
}
