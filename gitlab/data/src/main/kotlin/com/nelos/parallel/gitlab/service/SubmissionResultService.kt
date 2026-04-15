package com.nelos.parallel.gitlab.service

import com.nelos.parallel.commons.service.GenericService
import com.nelos.parallel.gitlab.entity.SubmissionResult

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface SubmissionResultService : GenericService<SubmissionResult> {

    fun findBySubmissionId(submissionId: Long): SubmissionResult?
}
