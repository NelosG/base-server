package com.nelos.parallel.gitlab.service

import com.nelos.parallel.commons.service.GenericService
import com.nelos.parallel.gitlab.entity.StudentGroup

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface StudentGroupService : GenericService<StudentGroup> {

    fun findByName(name: String): StudentGroup?
}
