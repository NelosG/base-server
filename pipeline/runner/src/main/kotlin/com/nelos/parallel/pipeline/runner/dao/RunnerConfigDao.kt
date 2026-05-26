package com.nelos.parallel.pipeline.runner.dao

import com.nelos.parallel.commons.dao.GenericDao
import com.nelos.parallel.pipeline.runner.entity.RunnerConfig

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface RunnerConfigDao : GenericDao<RunnerConfig> {

    fun findByName(name: String): RunnerConfig?
}
