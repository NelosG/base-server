package com.nelos.parallel.git.service

import com.nelos.parallel.git.vo.Repository
import java.nio.file.Path

/**
 * Service for downloading Git repositories.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface GitReacher {

    /**
     * Clones the given [repo] into a temporary directory and returns the path to it.
     */
    fun downloadRepo(repo: Repository): Path
}