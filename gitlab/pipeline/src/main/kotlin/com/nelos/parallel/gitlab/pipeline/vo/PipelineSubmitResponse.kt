package com.nelos.parallel.gitlab.pipeline.vo

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class PipelineSubmitResponse(
    val submissionId: Long,
    val status: String,
)
