package com.nelos.parallel.pipeline.data.dao.impl

import com.nelos.parallel.commons.dao.impl.GenericDaoImpl
import com.nelos.parallel.pipeline.data.dao.StudentGroupDao
import com.nelos.parallel.pipeline.data.entity.StudentGroup
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Repository("prl.studentGroupDao")
class StudentGroupDaoImpl : GenericDaoImpl<StudentGroup>(), StudentGroupDao {

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findByName(name: String): StudentGroup? {
        return findByCondition { cb, _, root ->
            cb.equal(root.get<String>("name"), name)
        }.firstOrNull()
    }
}
