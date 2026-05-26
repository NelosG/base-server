package com.nelos.parallel.runners.docker

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * JSON settings VO persisted in `prl_runner_config.settings` for the docker
 * runner. Edited via the /runners admin page.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class DockerRunnerSettings @JsonCreator constructor(
    @param:JsonProperty("dockerBinary") val dockerBinary: String = "docker",
    /** Image name + tag. Blank -> runner reports as unavailable. */
    @param:JsonProperty("imageName") val imageName: String = "",
    @param:JsonProperty("workdirBase") val workdirBase: String =
        System.getProperty("java.io.tmpdir") + "/parallel-docker",
    @param:JsonProperty("maxConcurrent") val maxConcurrent: Int = 1,
    /** Extra arguments passed to `docker run` BEFORE the image name. */
    @param:JsonProperty("extraArgs") val extraArgs: List<String> = emptyList(),
    /** Mount point inside the container. Engine cli sees `/work/<sub>` paths. */
    @param:JsonProperty("containerWorkdir") val containerWorkdir: String = "/work",
    @param:JsonProperty("shutdownGraceSec") val shutdownGraceSec: Int = 15,
)
