package com.nelos.parallel.pipeline.forms

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import com.nelos.parallel.pipeline.forms.vo.RunnerRowView
import com.nelos.parallel.pipeline.forms.vo.SaveRunnerRequest
import com.nelos.parallel.pipeline.runner.entity.RunnerConfig
import com.nelos.parallel.pipeline.runner.service.RunnerConfigService
import com.nelos.parallel.pipeline.runner.service.TaskRunner

/**
 * Backs the /runners admin page. Lists every `TaskRunner` bean in the build
 * (joined with its `prl_runner_config` row, if any) and persists admin
 * edits back into the table.
 *
 * Runners present in the build but missing from the DB show as `enabled=false`
 * with default priority. Saving such a row creates the DB entry.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.runnerConfigViewService", roles = [AppRole.ADMIN])
class RunnerConfigViewService(
    private val runners: List<TaskRunner>,
    private val runnerConfigService: RunnerConfigService,
    private val objectMapper: ObjectMapper,
) {

    fun list(): List<RunnerRowView> {
        val configs = runnerConfigService.findAll().associateBy { it.name }
        return runners.map { runner ->
            val cfg = configs[runner.name]
            RunnerRowView(
                name = runner.name,
                type = runner.type,
                hasSettings = runner.type.hasSettings,
                available = runner.isAvailable(),
                // Default to enabled: a runner present in the build but not yet
                // explicitly toggled in the UI is considered "on". Admin has to
                // save with enabled=false to opt it out.
                enabled = cfg?.enabled ?: true,
                priority = cfg?.priority ?: DEFAULT_PRIORITY,
                settingsJson = cfg?.settings?.toString(),
                id = cfg?.id,
            )
        }.sortedWith(compareBy({ !it.enabled }, { -it.priority }, { it.name }))
    }

    fun save(request: SaveRunnerRequest) {
        val name = request.name.trim()
        require(name.isNotBlank()) { "name is required" }
        require(runners.any { it.name == name }) { "no runner bean named '$name'" }

        // Runners that don't declare settings (HTTP, AMQP) cannot carry a
        // settings blob - the UI hides the field, but a hand-crafted call to
        // save() shouldn't be able to sneak one through either.
        val runnerType = runners.first { it.name == name }.type
        val settingsNode: ObjectNode? = request.settingsJson
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { runnerType.hasSettings }
            ?.let { raw ->
                val node = try {
                    objectMapper.readTree(raw)
                } catch (e: Exception) {
                    error("settingsJson is not valid JSON: ${e.message}")
                }
                require(node.isObject) { "settingsJson must be a JSON object, got ${node.nodeType}" }
                node as ObjectNode
            }

        val existing = runnerConfigService.findByName(name) ?: RunnerConfig().apply { this.name = name }
        existing.enabled = request.enabled
        existing.priority = request.priority
        existing.settings = settingsNode
        runnerConfigService.save(existing)
    }

    fun delete(id: Long) {
        runnerConfigService.remove(id)
    }

    companion object {
        // Mirrors `prl_runner_config.priority` column default.
        // Higher number = higher priority; default 0 means "not prioritized".
        private const val DEFAULT_PRIORITY = 0
    }
}
