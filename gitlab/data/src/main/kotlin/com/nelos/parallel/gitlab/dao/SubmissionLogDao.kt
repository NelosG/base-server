package com.nelos.parallel.gitlab.dao

import com.nelos.parallel.commons.dao.GenericDao
import com.nelos.parallel.gitlab.entity.SubmissionLog

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface SubmissionLogDao : GenericDao<SubmissionLog> {

    fun findBySubmissionId(submissionId: Long): List<SubmissionLog>
}
