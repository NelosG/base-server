package com.nelos.parallel.core.service.impl

import com.nelos.parallel.core.dao.CodeDao
import com.nelos.parallel.core.entity.CodeEntity
import com.nelos.parallel.core.service.CodeService
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
abstract class CodeServiceImpl<T : CodeEntity, DAO : CodeDao<T>> :
    GenericServiceImpl<T, DAO>(), CodeService<T> {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByName(name: String): List<T> {
        return invokeDaoMethod { it.findByName(name) }
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByCode(code: String): T? {
        return invokeDaoMethod { it.findByCode(code) }
    }
}
