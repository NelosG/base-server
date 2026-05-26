package com.nelos.parallel.pipeline.forms

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.pipeline.forms.vo.SaveRunnerRequest
import com.nelos.parallel.pipeline.runner.entity.RunnerConfig
import com.nelos.parallel.pipeline.runner.service.RunHandle
import com.nelos.parallel.pipeline.runner.service.RunnerConfigService
import com.nelos.parallel.pipeline.runner.service.RunnerContext
import com.nelos.parallel.pipeline.runner.service.RunnerType
import com.nelos.parallel.pipeline.runner.service.TaskRunner
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*

class RunnerConfigViewServiceTest {

    private val configService: RunnerConfigService = mock()
    private val objectMapper = ObjectMapper()

    private val httpRunner = stubRunner("http", RunnerType.HTTP, available = true)
    private val localRunner = stubRunner("local", RunnerType.LOCAL, available = false)

    private lateinit var svc: RunnerConfigViewService

    @BeforeEach
    fun setUp() {
        svc = RunnerConfigViewService(
            runners = listOf(httpRunner, localRunner),
            runnerConfigService = configService,
            objectMapper = objectMapper,
        )
    }

    private fun stubRunner(name: String, type: RunnerType, available: Boolean): TaskRunner = object : TaskRunner {
        override val type = type
        override val name = name
        override fun isAvailable() = available
        override fun tryRun(ctx: RunnerContext): RunHandle? = null
    }

    @Test
    fun `list returns one row per detected bean joined with config`() {
        whenever(configService.findAll()).thenReturn(
            listOf(RunnerConfig().apply {
                id = 1L; name = "http"; enabled = true; priority = 5
            })
        )
        val rows = svc.list()
        assertEquals(2, rows.size)
        val http = rows.first { it.name == "http" }
        assertTrue(http.enabled)
        assertEquals(5, http.priority)
        assertEquals(1L, http.id)
        // No DB row -> default to enabled with priority 0.
        val local = rows.first { it.name == "local" }
        assertTrue(local.enabled, "runner without a DB row should default to enabled")
        assertEquals(0, local.priority)
        assertFalse(local.available)
        assertNull(local.id)
    }

    @Test
    fun `list places higher priority before lower with disabled last`() {
        whenever(configService.findAll()).thenReturn(
            listOf(
                RunnerConfig().apply { name = "local"; enabled = false; priority = 99 },
                RunnerConfig().apply { name = "http"; enabled = true; priority = 1 },
            )
        )
        val rows = svc.list()
        // Both registered runners are http (enabled, p=1) and local (disabled, p=99).
        // Enabled-first ordering keeps http on top regardless of its lower priority.
        assertEquals("http", rows[0].name)
        assertEquals("local", rows[1].name)
    }

    @Test
    fun `list exposes hasSettings per runner type`() {
        whenever(configService.findAll()).thenReturn(emptyList())
        val rows = svc.list()
        val http = rows.first { it.name == "http" }
        val local = rows.first { it.name == "local" }
        assertFalse(http.hasSettings, "HTTP runners read live state, no JSON settings")
        assertTrue(local.hasSettings, "LOCAL runners carry editable settings")
    }

    @Test
    fun `save creates a new row when none exists yet`() {
        whenever(configService.findByName("local")).thenReturn(null)
        svc.save(SaveRunnerRequest(
            name = "local",
            enabled = true,
            priority = 3,
            settingsJson = """{"binaryPath":"/usr/local/bin/cli"}""",
        ))
        argumentCaptor<RunnerConfig>().apply {
            verify(configService).save(capture())
            val saved = firstValue
            assertEquals("local", saved.name)
            assertTrue(saved.enabled)
            assertEquals(3, saved.priority)
            assertEquals("/usr/local/bin/cli", saved.settings?.get("binaryPath")?.asText())
        }
    }

    @Test
    fun `save updates existing row in place`() {
        val existing = RunnerConfig().apply {
            id = 7L; name = "http"; enabled = false; priority = 99
        }
        whenever(configService.findByName("http")).thenReturn(existing)
        svc.save(SaveRunnerRequest(name = "http", enabled = true, priority = 1, settingsJson = null))
        argumentCaptor<RunnerConfig>().apply {
            verify(configService).save(capture())
            assertSame(existing, firstValue)
            assertEquals(7L, firstValue.id)
            assertTrue(firstValue.enabled)
            assertEquals(1, firstValue.priority)
            assertNull(firstValue.settings)
        }
    }

    @Test
    fun `save drops settings for runner types that don't support them`() {
        whenever(configService.findByName("http")).thenReturn(null)
        // HTTP runner: type.hasSettings = false. Even if a settings blob is
        // posted, it should be ignored - the row gets settings=null.
        svc.save(SaveRunnerRequest(name = "http", enabled = true, priority = 1,
            settingsJson = """{"sneaky":"value"}"""))
        argumentCaptor<RunnerConfig>().apply {
            verify(configService).save(capture())
            assertNull(firstValue.settings, "settings should be ignored for HTTP runner")
        }
    }

    @Test
    fun `save rejects unknown runner names`() {
        val ex = assertThrows<IllegalArgumentException> {
            svc.save(SaveRunnerRequest(name = "ghost", enabled = true, priority = 1, settingsJson = null))
        }
        assertTrue(ex.message?.contains("ghost") == true)
        verify(configService, never()).save(any<RunnerConfig>())
    }

    @Test
    fun `save rejects malformed JSON`() {
        val ex = assertThrows<IllegalStateException> {
            svc.save(SaveRunnerRequest(name = "local", enabled = true, priority = 1, settingsJson = "{not-json"))
        }
        assertTrue(ex.message?.contains("not valid JSON") == true)
        verify(configService, never()).save(any<RunnerConfig>())
    }

    @Test
    fun `save rejects non-object JSON`() {
        val ex = assertThrows<IllegalArgumentException> {
            svc.save(SaveRunnerRequest(name = "local", enabled = true, priority = 1, settingsJson = """["a","b"]"""))
        }
        assertTrue(ex.message?.contains("must be a JSON object") == true)
    }

    @Test
    fun `delete forwards to service`() {
        svc.delete(11L)
        verify(configService).remove(11L)
    }
}
