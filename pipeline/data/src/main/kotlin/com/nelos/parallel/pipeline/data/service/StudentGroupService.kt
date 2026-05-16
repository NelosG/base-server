package com.nelos.parallel.pipeline.data.service

import com.nelos.parallel.commons.service.GenericService
import com.nelos.parallel.pipeline.data.entity.StudentGroup

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface StudentGroupService : GenericService<StudentGroup> {

    fun findByName(name: String): StudentGroup?
}
