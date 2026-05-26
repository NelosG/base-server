package com.nelos.parallel.pipeline.runner.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.pipeline.runner.service.RunnerConfigService
import com.nelos.parallel.pipeline.runner.service.RunnerSettingsProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Reads `prl_runner_config.settings` and maps the JSON onto a typed VO via
 * Jackson. Falls back to caller-supplied defaults whenever the row is
 * missing, the JSON is null, or deserialization fails.
 *
 * Not cached - admin edits via /runners must take effect on the next
 * dispatch. The DB hit is one row lookup by indexed `name`, comparable to
 * the existing health-check chatter that already runs per submission.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.runnerSettingsProvider")
class RunnerSettingsProviderImpl(
    private val runnerConfigService: RunnerConfigService,
    private val objectMapper: ObjectMapper,
) : RunnerSettingsProvider {

    override fun <T : Any> get(runnerName: String, type: Class<T>, defaults: () -> T): T {
        val node = runnerConfigService.findByName(runnerName)?.settings ?: return defaults()
        return try {
            objectMapper.treeToValue(node, type)
        } catch (e: Exception) {
            LOG.warn(
                "runner {} settings JSON does not match {}: {} - falling back to defaults",
                runnerName, type.simpleName, e.message,
            )
            defaults()
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RunnerSettingsProviderImpl::class.java)
    }
}
