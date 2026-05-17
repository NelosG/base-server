package com.nelos.parallel.commons.adapter.listener

import com.nelos.parallel.commons.adapter.vo.response.TaskResult
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class TaskResultListenerRegistryTest {

    private val registry = TaskResultListenerRegistry()

    private fun result(jobId: String? = "j-1") =
        TaskResult(jobId = jobId ?: "ignored", status = "completed")

    @Test
    fun `dispatch hands the result to a registered listener`() {
        var captured: TaskResult? = null
        registry.register("j-1") { captured = it }
        val r = result("j-1")

        registry.dispatch(r)

        assertSame(r, captured)
    }

    @Test
    fun `dispatch is a no-op when no listener is registered for the jobId`() {
        // Just must not throw - a missing listener is normal (no one polled).
        registry.dispatch(result("j-unknown"))
    }

    @Test
    fun `dispatch is one-shot - the second delivery for the same jobId is dropped`() {
        val calls = AtomicInteger(0)
        registry.register("j-1") { calls.incrementAndGet() }
        val r = result("j-1")

        registry.dispatch(r)
        registry.dispatch(r)

        assertEquals(1, calls.get())
    }

    @Test
    fun `unregister cancels a pending listener`() {
        val calls = AtomicInteger(0)
        registry.register("j-1") { calls.incrementAndGet() }

        registry.unregister("j-1")
        registry.dispatch(result("j-1"))

        assertEquals(0, calls.get())
    }

    @Test
    fun `listener throwing does not propagate and consumes the registration`() {
        val calls = AtomicInteger(0)
        registry.register("j-1") {
            calls.incrementAndGet()
            error("intentional")
        }

        // Must not throw.
        registry.dispatch(result("j-1"))
        // And the listener was still removed - second dispatch is a no-op.
        registry.dispatch(result("j-1"))

        assertEquals(1, calls.get())
    }

}
