package com.nelos.parallel.pipeline.commons.enums

/**
 * Language of an instructor-written submission-verdict script attached to an
 * assignment. Each value has a matching runtime registered as a
 * [com.nelos.parallel.pipeline.commons.extension.ScriptVerdictExtension] -
 * if the runtime isn't on the classpath, the evaluator falls back to the
 * default test-count verdict and surfaces a warning.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
enum class ScriptType {
    KTS,
    PYTHON,
}
