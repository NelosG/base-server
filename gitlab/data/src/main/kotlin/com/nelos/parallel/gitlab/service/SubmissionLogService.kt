package com.nelos.parallel.gitlab.service

import com.nelos.parallel.commons.service.GenericService
import com.nelos.parallel.gitlab.entity.SubmissionLog

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface SubmissionLogService : GenericService<SubmissionLog> {

    fun findBySubmissionId(submissionId: Long): List<SubmissionLog>
}
