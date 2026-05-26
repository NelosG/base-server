package com.nelos.parallel.pipeline.kts.api

import com.nelos.parallel.pipeline.commons.extension.JudgeContext
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

/**
 * Script definition for instructor verdict scripts. Drives both compilation
 * (default imports, provided properties, classpath) and evaluation (the
 * actual `ctx` instance is bound per-run in [com.nelos.parallel.pipeline.kts.KtsVerdictExtension]).
 *
 * The defaultImports list spares instructors from writing `import` blocks
 * for the helper API and the data classes the scripts naturally reach into
 * (`TaskResult`, `TestSummary`, `SubmissionStatus`, ...). Anything outside
 * these packages still needs an explicit import.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@KotlinScript(
    displayName = "Verdict Script",
    fileExtension = "verdict.kts",
    compilationConfiguration = VerdictScriptCompilation::class,
)
abstract class VerdictScript

object VerdictScriptCompilation : ScriptCompilationConfiguration({
    defaultImports(
        "com.nelos.parallel.pipeline.kts.api.*",
        "com.nelos.parallel.pipeline.commons.extension.*",
        "com.nelos.parallel.pipeline.commons.enums.*",
        "com.nelos.parallel.commons.adapter.vo.response.*",
        "com.nelos.parallel.jobs.enums.*",
    )
    providedProperties("ctx" to KotlinType(JudgeContext::class))
    jvm {
        // Pull the whole host classpath so scripts can see TaskResult,
        // TestSummary, JudgeContext and the API helpers without further
        // configuration. Works in plain Maven/IDE runs and in Spring Boot
        // exploded layouts; if we ever ship a fat jar we may need to add
        // an explicit dependenciesFromClassloader fallback.
        dependenciesFromCurrentContext(wholeClasspath = true)
    }
})
