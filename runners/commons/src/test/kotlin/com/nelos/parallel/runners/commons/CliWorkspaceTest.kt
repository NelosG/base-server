package com.nelos.parallel.runners.commons

import com.nelos.parallel.commons.adapter.enums.SourceType
import com.nelos.parallel.commons.adapter.vo.request.SourceDescriptor
import com.nelos.parallel.commons.adapter.vo.request.TaskSubmission
import com.nelos.parallel.git.service.GitReacher
import com.nelos.parallel.git.vo.Repository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class CliWorkspaceTest {

    @TempDir
    lateinit var tempDir: Path

    private val gitReacher: GitReacher = mock()

    private fun workspace(): CliWorkspace = CliWorkspace(tempDir.resolve("job-1"), gitReacher)

    @Test
    fun `path accessors compose subdirs under the workspace root`() {
        val ws = workspace()
        assertEquals(tempDir.resolve("job-1").resolve("tests"), ws.testsDir)
        assertEquals(tempDir.resolve("job-1").resolve("solution"), ws.solutionDir)
        assertEquals(tempDir.resolve("job-1").resolve("result.json"), ws.resultFile)
    }

    @Test
    fun `prepare with LocalSource copies the directory tree into the workspace`() {
        val src = Files.createDirectories(tempDir.resolve("src"))
        Files.writeString(src.resolve("a.txt"), "hello")
        val sub = Files.createDirectories(src.resolve("sub"))
        Files.writeString(sub.resolve("b.txt"), "world")
        val ws = workspace()
        val task = TaskSubmission(
            testSourceType = SourceType.LOCAL,
            testSource = SourceDescriptor.LocalSource(src.toString()),
            solutionSourceType = SourceType.LOCAL,
            solutionSource = SourceDescriptor.LocalSource(src.toString()),
        )

        ws.prepare(task)

        assertEquals("hello", Files.readString(ws.testsDir.resolve("a.txt")))
        assertEquals("world", Files.readString(ws.testsDir.resolve("sub").resolve("b.txt")))
        assertEquals("hello", Files.readString(ws.solutionDir.resolve("a.txt")))
    }

    @Test
    fun `prepare with GitSource clones via GitReacher and moves the result under the target dir`() {
        val cloned = Files.createDirectories(tempDir.resolve("cloned-repo"))
        Files.writeString(cloned.resolve("README.md"), "# repo")
        whenever(gitReacher.downloadRepo(any())).thenReturn(cloned)
        val solutionSrc = Files.createDirectories(tempDir.resolve("solution-src"))

        val task = TaskSubmission(
            testSourceType = SourceType.GIT,
            testSource = SourceDescriptor.GitSource(
                url = "https://gitlab/example.git",
                branch = "main",
                token = "pat-secret",
            ),
            solutionSourceType = SourceType.LOCAL,
            solutionSource = SourceDescriptor.LocalSource(solutionSrc.toString()),
        )

        workspace().prepare(task)

        val captor = argumentCaptor<Repository>()
        verify(gitReacher).downloadRepo(captor.capture())
        val repo = captor.firstValue
        assertEquals("https://gitlab/example.git", repo.uri)
        assertEquals("main", repo.branch)
        assertEquals("pat-secret", repo.token)
    }

    @Test
    fun `prepare with GitSource without an explicit branch keeps the builder default`() {
        val cloned = Files.createDirectories(tempDir.resolve("cloned-repo-2"))
        whenever(gitReacher.downloadRepo(any())).thenReturn(cloned)
        val solutionSrc = Files.createDirectories(tempDir.resolve("solution-src-2"))

        val task = TaskSubmission(
            testSourceType = SourceType.GIT,
            testSource = SourceDescriptor.GitSource(
                url = "https://gitlab/example.git",
                branch = null,
                token = "pat-secret",
            ),
            solutionSourceType = SourceType.LOCAL,
            solutionSource = SourceDescriptor.LocalSource(solutionSrc.toString()),
        )

        workspace().prepare(task)

        val captor = argumentCaptor<Repository>()
        verify(gitReacher).downloadRepo(captor.capture())
        // Default branch from Repository.Builder is "master" - if it ever flips
        // to something else (e.g. "main") the runner contract changes too.
        assertEquals("master", captor.firstValue.branch)
    }

    @Test
    fun `GitSource without a token is rejected - we will not run unauthenticated against private repos`() {
        val task = TaskSubmission(
            testSourceType = SourceType.GIT,
            testSource = SourceDescriptor.GitSource(url = "https://gitlab/p.git", token = null),
            solutionSourceType = SourceType.LOCAL,
            solutionSource = SourceDescriptor.LocalSource(tempDir.toString()),
        )

        val ex = assertThrows<IllegalStateException> { workspace().prepare(task) }
        assertContains(ex.message ?: "", "no token")
    }

    @Test
    fun `LocalSource pointing at a non-existent directory is rejected`() {
        val task = TaskSubmission(
            testSourceType = SourceType.LOCAL,
            testSource = SourceDescriptor.LocalSource(tempDir.resolve("does-not-exist").toString()),
            solutionSourceType = SourceType.LOCAL,
            solutionSource = SourceDescriptor.LocalSource(tempDir.toString()),
        )

        val ex = assertThrows<IllegalStateException> { workspace().prepare(task) }
        assertContains(ex.message ?: "", "does not exist")
    }

    @Test
    fun `missing source descriptor is rejected with the label in the error message`() {
        val task = TaskSubmission(
            testSourceType = SourceType.LOCAL,
            testSource = null,
            solutionSourceType = SourceType.LOCAL,
            solutionSource = SourceDescriptor.LocalSource(tempDir.toString()),
        )

        val ex = assertThrows<IllegalStateException> { workspace().prepare(task) }
        assertContains(ex.message ?: "", "tests")
    }

    @Test
    fun `cleanup removes the workspace tree even when it has nested files`() {
        val src = Files.createDirectories(tempDir.resolve("seed"))
        Files.writeString(src.resolve("a.txt"), "x")
        val ws = workspace()
        ws.prepare(
            TaskSubmission(
                testSourceType = SourceType.LOCAL,
                testSource = SourceDescriptor.LocalSource(src.toString()),
                solutionSourceType = SourceType.LOCAL,
                solutionSource = SourceDescriptor.LocalSource(src.toString()),
            ),
        )
        assertTrue(Files.exists(ws.testsDir))

        ws.cleanup()

        assertFalse(Files.exists(tempDir.resolve("job-1")))
    }

    @Test
    fun `cleanup never throws - failure is logged only`() {
        // Workspace that was never created - cleanup is a no-op, must not throw.
        val ws = CliWorkspace(tempDir.resolve("ghost"), gitReacher)
        ws.cleanup()
    }
}
