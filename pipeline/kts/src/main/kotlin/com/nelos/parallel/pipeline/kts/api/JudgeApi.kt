@file:JvmName("JudgeApi")

package com.nelos.parallel.pipeline.kts.api

import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.commons.extension.JudgeContext
import com.nelos.parallel.pipeline.commons.extension.JudgeResult

/**
 * DSL surface exposed to instructor-authored .kts verdict scripts. Scripts
 * use these top-level helpers as if they were native Kotlin keywords:
 *
 *     judge { ctx ->
 *         if (ctx.result.summary?.performance?.failed == 0) pass()
 *         else fail("perf had failures")
 *     }
 *
 * The runner ([com.nelos.parallel.pipeline.kts.KtsVerdictExtension]) sets
 * [currentJudgeContext] on its worker thread before running the script.
 * The script's last expression is normally the `judge { }` call (which
 * returns its lambda's result), so the runner reads the verdict directly
 * off the script's return value. As a belt-and-braces fallback we also
 * remember the most-recent verdict in [currentVerdict] - handy if the
 * instructor writes additional code after the `judge` block.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */

internal val currentJudgeContext = ThreadLocal<JudgeContext?>()
internal val currentVerdict = ThreadLocal<JudgeResult?>()

fun judge(block: (JudgeContext) -> JudgeResult): JudgeResult {
    val ctx = currentJudgeContext.get()
        ?: error("judge() called outside a verdict-script runner - no JudgeContext is bound")
    val verdict = block(ctx)
    currentVerdict.set(verdict)
    return verdict
}

fun pass(summary: String? = null, reason: String? = null): JudgeResult =
    JudgeResult(pass = true, summary = summary, reason = reason)

fun fail(reason: String, summary: String? = null): JudgeResult =
    JudgeResult(pass = false, summary = summary, reason = reason)

fun followBaseline(reason: String? = null): JudgeResult {
    val ctx = currentJudgeContext.get()
        ?: error("followBaseline() called outside a verdict-script runner - no JudgeContext is bound")
    return JudgeResult(
        pass = ctx.baseline.submissionStatus == SubmissionStatus.COMPLETED,
        summary = ctx.baseline.summary,
        reason = reason ?: ctx.baseline.reason,
    )
}
