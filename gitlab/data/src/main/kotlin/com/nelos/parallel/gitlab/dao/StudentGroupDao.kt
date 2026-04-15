package com.nelos.parallel.gitlab.dao

import com.nelos.parallel.commons.dao.GenericDao
import com.nelos.parallel.gitlab.entity.StudentGroup

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface StudentGroupDao : GenericDao<StudentGroup> {

    fun findByName(name: String): StudentGroup?
}
