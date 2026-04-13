package com.nelos.parallel.jobs.service.impl

import com.nelos.parallel.commons.service.impl.GenericServiceImpl
import com.nelos.parallel.jobs.dao.JobDao
import com.nelos.parallel.jobs.entity.Job
import com.nelos.parallel.jobs.service.JobService
import org.springframework.stereotype.Service

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.jobService")
class JobServiceImpl : GenericServiceImpl<Job, JobDao>(), JobService
