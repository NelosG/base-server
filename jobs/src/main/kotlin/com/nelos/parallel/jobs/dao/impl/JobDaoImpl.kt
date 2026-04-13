package com.nelos.parallel.jobs.dao.impl

import com.nelos.parallel.commons.dao.impl.GenericDaoImpl
import com.nelos.parallel.jobs.dao.JobDao
import com.nelos.parallel.jobs.entity.Job
import org.springframework.stereotype.Repository

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Repository("prl.jobDao")
class JobDaoImpl : GenericDaoImpl<Job>(), JobDao
