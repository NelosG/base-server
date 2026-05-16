package com.nelos.parallel.pipeline.data.dao

import com.nelos.parallel.commons.dao.GenericDao
import com.nelos.parallel.pipeline.data.entity.SubmissionResult

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface SubmissionResultDao : GenericDao<SubmissionResult> {

    fun findBySubmissionId(submissionId: Long): SubmissionResult?
}
