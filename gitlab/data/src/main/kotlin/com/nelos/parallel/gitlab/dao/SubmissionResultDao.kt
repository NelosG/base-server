package com.nelos.parallel.gitlab.dao

import com.nelos.parallel.commons.dao.GenericDao
import com.nelos.parallel.gitlab.entity.SubmissionResult

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface SubmissionResultDao : GenericDao<SubmissionResult> {

    fun findBySubmissionId(submissionId: Long): SubmissionResult?
}
