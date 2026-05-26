package com.nelos.parallel.runners.commons

import com.nelos.parallel.commons.adapter.enums.SourceType
import com.nelos.parallel.commons.adapter.vo.request.SourceDescriptor
import com.nelos.parallel.commons.adapter.vo.request.TaskSubmission
import com.nelos.parallel.git.service.GitReacher
import com.nelos.parallel.git.vo.Repository
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Materializes the test repo and the solution repo under a job-local
 * workspace so the cli binary can be fed `--test-dir` / `--solution`
 * absolute paths. Shared between local and docker runners.
 *
 *   workspaceRoot/
 *     <jobId>/
 *       tests/         <- materialized test repo (git or local)
 *       solution/      <- materialized solution repo (git or local)
 *       result.json    <- written by cli (output)
 *
 * Pre-cloning here, instead of letting cli do `--test-url` / `--solution-url`,
 * keeps secrets (PAT tokens) out of subprocess argv on shared dev hosts.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class CliWorkspace(
    private val root: Path,
    private val gitReacher: GitReacher,
) {

    val testsDir: Path get() = root.resolve("tests")
    val solutionDir: Path get() = root.resolve("solution")
    val resultFile: Path get() = root.resolve("result.json")

    fun prepare(task: TaskSubmission) {
        Files.createDirectories(root)
        materialize(task.testSourceType, task.testSource, testsDir, label = "tests")
        materialize(task.solutionSourceType, task.solutionSource, solutionDir, label = "solution")
    }

    fun cleanup() {
        try {
            root.toFile().deleteRecursively()
        } catch (e: Exception) {
            LOG.warn("workspace cleanup of {} failed: {}", root, e.message)
        }
    }

    private fun materialize(type: SourceType?, source: SourceDescriptor?, target: Path, label: String) {
        when (source) {
            is SourceDescriptor.GitSource -> {
                val branch = source.branch
                val token = source.token
                    ?: error("$label source is a Git URL but no token was provided")
                val repoBuilder = Repository.builder()
                    .uri(source.url)
                    .token(token)
                if (branch != null) repoBuilder.branch(branch)
                val cloned = gitReacher.downloadRepo(repoBuilder.build())
                moveTree(cloned, target)
            }
            is SourceDescriptor.LocalSource -> {
                val src = Path.of(source.path)
                if (!Files.isDirectory(src)) error("$label local path does not exist: $src")
                copyTree(src, target)
            }
            null -> error("$label source is missing (type=$type)")
        }
    }

    /** Move the freshly-cloned tree under `<workspace>/<label>` and drop the parent. */
    private fun moveTree(src: Path, target: Path) {
        Files.createDirectories(target.parent)
        try {
            Files.move(src, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            copyTree(src, target)
            src.toFile().deleteRecursively()
        }
    }

    private fun copyTree(src: Path, target: Path) {
        Files.createDirectories(target)
        src.toFile().walkTopDown().forEach { f ->
            val rel = src.relativize(f.toPath())
            val dst = target.resolve(rel)
            if (f.isDirectory) Files.createDirectories(dst)
            else Files.copy(f.toPath(), dst, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(CliWorkspace::class.java)
    }
}
