package com.nelos.parallel.pipeline.runner.service

import com.nelos.parallel.commons.service.GenericService
import com.nelos.parallel.pipeline.runner.entity.RunnerConfig

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface RunnerConfigService : GenericService<RunnerConfig> {

    fun findByName(name: String): RunnerConfig?
}
