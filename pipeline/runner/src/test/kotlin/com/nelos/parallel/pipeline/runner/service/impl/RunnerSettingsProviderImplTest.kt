package com.nelos.parallel.pipeline.runner.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.nelos.parallel.pipeline.runner.entity.RunnerConfig
import com.nelos.parallel.pipeline.runner.service.RunnerConfigService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class RunnerSettingsProviderImplTest {

    private val configService: RunnerConfigService = mock()
    private val mapper = ObjectMapper()
    private val provider = RunnerSettingsProviderImpl(configService, mapper)

    class TestSettings(val flag: Boolean = false, val count: Int = 0, val name: String = "")
    class DefaultSettings(val marker: String = "default")

    @Test
    fun `returns defaults when there is no row for the runner`() {
        whenever(configService.findByName("local")).thenReturn(null)
        val expected = DefaultSettings()

        val result = provider.get("local", DefaultSettings::class.java) { expected }

        assertSame(expected, result)
    }

    @Test
    fun `returns defaults when the row exists but has no settings JSON`() {
        whenever(configService.findByName("local")).thenReturn(
            RunnerConfig().apply { name = "local"; settings = null },
        )
        val expected = DefaultSettings("from-defaults")

        val result = provider.get("local", DefaultSettings::class.java) { expected }

        assertEquals("from-defaults", result.marker)
    }

    @Test
    fun `deserializes the settings JSON into the requested type`() {
        val payload: ObjectNode = mapper.createObjectNode()
            .put("flag", true)
            .put("count", 42)
            .put("name", "ci")
        whenever(configService.findByName("local")).thenReturn(
            RunnerConfig().apply { name = "local"; settings = payload },
        )

        val result = provider.get("local", TestSettings::class.java) { TestSettings() }

        assertEquals(true, result.flag)
        assertEquals(42, result.count)
        assertEquals("ci", result.name)
    }

    @Test
    fun `falls back to defaults when JSON shape doesn't match the requested type`() {
        // The persisted blob was meant for a DIFFERENT runner (LocalRunnerSettings)
        // and got requested with DockerRunnerSettings - a typo in the admin UI
        // shouldn't blow up dispatch. We log + return defaults.
        val payload: ObjectNode = mapper.createObjectNode()
            .put("count", "not-a-number") // type mismatch on Int field
        whenever(configService.findByName("local")).thenReturn(
            RunnerConfig().apply { name = "local"; settings = payload },
        )

        val result = provider.get("local", TestSettings::class.java) { TestSettings(name = "fallback") }

        assertEquals("fallback", result.name)
    }
}
