package com.nelos.parallel.git.service.impl

import com.nelos.parallel.git.service.GitReacher
import com.nelos.parallel.git.vo.Repository
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

/**
 * [com.nelos.parallel.git.service.GitReacher] implementation that uses JGit for shallow-cloning repositories over HTTPS.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.gitReacher")
class GitReacherImpl : GitReacher {

    override fun downloadRepo(repo: Repository): Path {
        val credentials = when (repo.token) {
            null -> UsernamePasswordCredentialsProvider(repo.userName, repo.password)
            else -> UsernamePasswordCredentialsProvider(repo.token, "")
        }

        val tempDir = Files.createTempDirectory("git-repo")
        try {
            Git.cloneRepository()
                .setURI(repo.uri)
                .setBranch(repo.branch)
                .setDepth(1)
                .setTimeout(TIMEOUT_SEC)
                .setCredentialsProvider(credentials)
                .setDirectory(tempDir.toFile())
                .call()
                .use { /* close Git resource */ }
        } catch (e: Exception) {
            tempDir.toFile().deleteRecursively()
            throw e
        }

        return tempDir
    }

    companion object {
        const val TIMEOUT_SEC = 30
    }
}