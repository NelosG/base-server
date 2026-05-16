package com.nelos.parallel.pipeline.data.dao

import com.nelos.parallel.commons.dao.GenericDao
import com.nelos.parallel.pipeline.data.entity.SubmissionLog

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface SubmissionLogDao : GenericDao<SubmissionLog> {

    fun findBySubmissionId(submissionId: Long): List<SubmissionLog>
}
