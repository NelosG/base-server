package com.nelos.parallel.runners.local

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * JSON settings VO persisted in `prl_runner_config.settings` for the local
 * runner. Edited via the /runners admin page. All fields nullable / have
 * defaults so a freshly-created row with no JSON body still produces a usable
 * (if disabled) runner.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class LocalRunnerSettings @JsonCreator constructor(
    /** Absolute path to the cli binary. Blank -> runner reports as unavailable. */
    @param:JsonProperty("binaryPath") val binaryPath: String = "",

    /** Root directory for per-job workspaces. */
    @param:JsonProperty("workdirBase") val workdirBase: String =
        System.getProperty("java.io.tmpdir") + "/parallel-cli",

    /** Soft limit on concurrent cli processes (cli is already multi-threaded). */
    @param:JsonProperty("maxConcurrent") val maxConcurrent: Int = 1,

    /** Seconds to wait for graceful process termination before destroyForcibly. */
    @param:JsonProperty("shutdownGraceSec") val shutdownGraceSec: Int = 10,
)
