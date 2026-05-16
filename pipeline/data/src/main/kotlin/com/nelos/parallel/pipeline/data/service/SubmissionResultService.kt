package com.nelos.parallel.pipeline.data.service

import com.nelos.parallel.commons.service.GenericService
import com.nelos.parallel.pipeline.data.entity.SubmissionResult

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface SubmissionResultService : GenericService<SubmissionResult> {

    fun findBySubmissionId(submissionId: Long): SubmissionResult?
}
