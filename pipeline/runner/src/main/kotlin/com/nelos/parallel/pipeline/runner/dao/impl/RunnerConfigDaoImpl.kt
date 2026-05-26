package com.nelos.parallel.pipeline.runner.dao.impl

import com.nelos.parallel.commons.dao.impl.GenericDaoImpl
import com.nelos.parallel.pipeline.runner.dao.RunnerConfigDao
import com.nelos.parallel.pipeline.runner.entity.RunnerConfig
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Repository("prl.runnerConfigDao")
class RunnerConfigDaoImpl : GenericDaoImpl<RunnerConfig>(), RunnerConfigDao {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByName(name: String): RunnerConfig? {
        return findByCondition(1) { cb, _, root ->
            cb.equal(root.get<String>("name"), name)
        }.firstOrNull()
    }
}
