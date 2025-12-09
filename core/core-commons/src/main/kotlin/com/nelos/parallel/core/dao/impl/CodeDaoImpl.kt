package com.nelos.parallel.core.dao.impl

import com.nelos.parallel.core.dao.CodeDao
import com.nelos.parallel.core.entity.CodeEntity
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
abstract class CodeDaoImpl<T : CodeEntity> : GenericDaoImpl<T>(), CodeDao<T> {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByName(name: String) = findByCondition { cb, _, root ->
        cb.equal(
            root.get<String>(CodeEntity::name.name), name
        )
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByCode(code: String): T? {
        val result = findByCondition { cb, _, root ->
            cb.equal(
                root.get<String>(CodeEntity::code.name), code
            )
        }
        if (result.size > 1) error("Found multiple entities by code: '$code'")
        return result.firstOrNull()
    }
}
