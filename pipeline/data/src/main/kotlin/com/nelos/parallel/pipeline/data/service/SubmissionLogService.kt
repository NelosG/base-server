package com.nelos.parallel.pipeline.data.service

import com.nelos.parallel.commons.service.GenericService
import com.nelos.parallel.pipeline.data.entity.SubmissionLog

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface SubmissionLogService : GenericService<SubmissionLog> {

    fun findBySubmissionId(submissionId: Long): List<SubmissionLog>
}
