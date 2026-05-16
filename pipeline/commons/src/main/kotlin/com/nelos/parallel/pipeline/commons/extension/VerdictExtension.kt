package com.nelos.parallel.pipeline.commons.extension

import com.nelos.parallel.pipeline.commons.service.EvaluationContext
import com.nelos.parallel.pipeline.commons.service.SubmissionVerdict

/**
 * Pluggable hook in the evaluator chain. The default test-count evaluator
 * produces a baseline verdict; every registered `VerdictExtension` then gets
 * the chance to inspect / replace it. Spring auto-collects all
 * `VerdictExtension` beans into a `List<VerdictExtension>` in the evaluator
 * impl, which folds them in registration order.
 *
 * Extensions that drive an instructor script (KTS / Python) implement
 * [ScriptVerdictExtension] instead so the evaluator can detect when a script
 * is configured but its runtime isn't on the classpath.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
fun interface VerdictExtension {

    /**
     * Inspect [current] and [context], return either the same verdict or a
     * modified / replaced one. Extensions MUST be idempotent and pure:
     * no DB writes, no network I/O, no global state mutation.
     */
    fun apply(context: EvaluationContext, current: SubmissionVerdict): SubmissionVerdict
}
