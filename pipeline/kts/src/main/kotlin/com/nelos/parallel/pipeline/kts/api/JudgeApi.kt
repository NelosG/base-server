@file:JvmName("JudgeApi")

package com.nelos.parallel.pipeline.kts.api

import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.commons.extension.JudgeContext
import com.nelos.parallel.pipeline.commons.extension.JudgeResult

/**
 * DSL surface exposed to instructor-authored .kts verdict scripts.
 *
 * The script runs in a context where `ctx: JudgeContext` is a provided
 * property (see [VerdictScript]) and the helpers below are auto-imported.
 * The script's last expression IS the verdict - return any [JudgeResult]
 * from `pass()`, `fail()`, `ctx.followBaseline()` or a hand-built one:
 *
 *     val perf = ctx.result.summary?.performance
 *     when {
 *         perf == null -> ctx.followBaseline("no perf data")
 *         (perf.passed ?: 0) >= (perf.totalTests ?: 0) -> pass()
 *         else -> fail("some perf tests failed")
 *     }
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */

fun pass(summary: String? = null, reason: String? = null): JudgeResult =
    JudgeResult(pass = true, summary = summary, reason = reason)

fun fail(reason: String, summary: String? = null): JudgeResult =
    JudgeResult(pass = false, summary = summary, reason = reason)

fun JudgeContext.followBaseline(reason: String? = null): JudgeResult =
    JudgeResult(
        pass = baseline.submissionStatus == SubmissionStatus.COMPLETED,
        summary = baseline.summary,
        reason = reason ?: baseline.reason,
    )
