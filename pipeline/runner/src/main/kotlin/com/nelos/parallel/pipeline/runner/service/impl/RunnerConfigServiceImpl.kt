package com.nelos.parallel.pipeline.runner.service.impl

import com.nelos.parallel.commons.service.impl.GenericServiceImpl
import com.nelos.parallel.pipeline.runner.dao.RunnerConfigDao
import com.nelos.parallel.pipeline.runner.entity.RunnerConfig
import com.nelos.parallel.pipeline.runner.service.RunnerConfigService
import org.springframework.stereotype.Service

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.runnerConfigService")
class RunnerConfigServiceImpl : GenericServiceImpl<RunnerConfig, RunnerConfigDao>(), RunnerConfigService {

    override fun findByName(name: String): RunnerConfig? = invokeDaoMethod { it.findByName(name) }
}
