package com.nelos.parallel.auth.dao.impl

import com.nelos.parallel.auth.dao.ApiKeyDao
import com.nelos.parallel.auth.entity.ApiKey
import com.nelos.parallel.commons.dao.impl.GenericDaoImpl
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Repository("prl.apiKeyDao")
class ApiKeyDaoImpl : GenericDaoImpl<ApiKey>(), ApiKeyDao {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByKeyHash(hash: String): ApiKey? {
        return findByCondition { cb, _, root ->
            cb.equal(root.get<String>(ApiKey::keyHash.name), hash)
        }.firstOrNull()
    }
}
