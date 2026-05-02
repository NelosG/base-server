package com.nelos.parallel.gitlab.pipeline.vo

import com.fasterxml.jackson.annotation.JsonInclude
import com.nelos.parallel.commons.adapter.vo.response.TaskResult

/**
 * Response for pipeline status polling. The CI job polls this until `finished` is true.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class PipelineStatusResponse(
    val submissionId: Long,
    val status: String,
    val finished: Boolean,
    val success: Boolean? = null,
    val logs: List<String>? = null,
    val result: TaskResult? = null,
    val resultSummary: String? = null,
)
