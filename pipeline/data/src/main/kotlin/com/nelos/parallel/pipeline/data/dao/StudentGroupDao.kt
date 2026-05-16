package com.nelos.parallel.pipeline.data.dao

import com.nelos.parallel.commons.dao.GenericDao
import com.nelos.parallel.pipeline.data.entity.StudentGroup

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface StudentGroupDao : GenericDao<StudentGroup> {

    fun findByName(name: String): StudentGroup?
}
