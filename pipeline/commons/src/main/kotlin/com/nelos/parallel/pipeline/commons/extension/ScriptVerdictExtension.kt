package com.nelos.parallel.pipeline.commons.extension

import com.nelos.parallel.pipeline.commons.enums.ScriptType

/**
 * Marker for [VerdictExtension] implementations that execute an
 * instructor-authored script in a specific language. Declaring the
 * [scriptType] lets the evaluator detect the "script configured, runtime
 * missing" case: if an assignment has a script of type `X` but no
 * `ScriptVerdictExtension` registered for `X`, the evaluator logs a warning
 * and falls back to the baseline verdict with a `reason` so the UI can
 * surface the warning to the instructor.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface ScriptVerdictExtension : VerdictExtension {

    val scriptType: ScriptType
}
