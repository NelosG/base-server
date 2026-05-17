package com.nelos.parallel.gitlab.forms

import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.gitlab.client.GitLabApiClient
import com.nelos.parallel.gitlab.client.vo.GitLabProjectInfo
import com.nelos.parallel.gitlab.forms.vo.CreateForksRequest
import com.nelos.parallel.gitlab.forms.vo.SaveAssignmentRequest
import com.nelos.parallel.gitlab.service.GitlabUserService
import com.nelos.parallel.pipeline.commons.enums.ScriptType
import com.nelos.parallel.pipeline.commons.service.EvaluatorScript
import com.nelos.parallel.pipeline.data.entity.Assignment
import com.nelos.parallel.pipeline.data.service.AssignmentService
import com.nelos.parallel.pipeline.data.service.StudentGroupMemberService
import com.nelos.parallel.pipeline.data.service.StudentGroupService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class AssignmentViewServiceTest {

    private val assignmentService: AssignmentService = mock()
    private val gitLabApiClient: GitLabApiClient = mock()
    private val groupService: StudentGroupService = mock()
    private val memberService: StudentGroupMemberService = mock()
    private val userService: UserService = mock()
    private val gitlabUserService: GitlabUserService = mock()

    private val service = AssignmentViewService(
        assignmentService, gitLabApiClient, groupService, memberService,
        userService, gitlabUserService, "https://gitlab.example.com",
    )

    private fun assignment(
        id: Long = 1L,
        code: String = "lab1",
        name: String = "Lab 1",
        gitlabProjectPath: String = "course/lab1",
        evaluatorScript: EvaluatorScript? = null,
    ): Assignment = Assignment().apply {
        this.id = id
        this.code = code
        this.name = name
        this.gitlabProjectPath = gitlabProjectPath
        this.evaluatorScript = evaluatorScript
    }

    private fun gitlabProject(
        namespace: String,
        webUrl: String = "https://gitlab.example.com/$namespace/lab1",
        forkedFrom: GitLabProjectInfo? = null,
        deleted: String? = null,
    ): GitLabProjectInfo = GitLabProjectInfo(
        name = "lab1",
        pathWithNamespace = "$namespace/lab1",
        webUrl = webUrl,
        forkedFromProject = forkedFrom,
        markedForDeletionAt = deleted,
    )

    // --- saveAssignment partial update ----------------------------------

    @Nested
    inner class SaveAssignment {

        @Test
        fun `creates new assignment when id is null`() {
            whenever(assignmentService.save(any<Assignment>())).thenAnswer { it.arguments[0] as Assignment }

            service.saveAssignment(
                SaveAssignmentRequest(code = "lab2", name = "Lab 2", gitlabProjectPath = "c/lab2"),
            )

            val saved = argumentCaptor<Assignment>()
            verify(assignmentService).save(saved.capture())
            assertEquals("lab2", saved.firstValue.code)
            assertEquals("Lab 2", saved.firstValue.name)
            // findById must NOT be called when id is null - fresh entity path.
            verify(assignmentService, never()).findById(any())
        }

        @Test
        fun `partial update keeps existing fields where the request is null`() {
            val existing = assignment(id = 1L, code = "old-code", name = "Old Name")
            existing.threads = 8
            existing.memoryLimitMb = 256L
            whenever(assignmentService.findById(1L)).thenReturn(existing)
            whenever(assignmentService.save(any<Assignment>())).thenAnswer { it.arguments[0] as Assignment }

            service.saveAssignment(SaveAssignmentRequest(id = 1L, name = "New Name"))

            val saved = argumentCaptor<Assignment>()
            verify(assignmentService).save(saved.capture())
            assertEquals("old-code", saved.firstValue.code) // untouched
            assertEquals("New Name", saved.firstValue.name) // updated
            assertEquals(8, saved.firstValue.threads)        // untouched
            assertEquals(256L, saved.firstValue.memoryLimitMb)
        }

        @Test
        fun `clearEvaluatorScript=true wipes the script even if evaluatorScript field is also provided`() {
            // The instructor toggled "remove script" but the front-end also happened to send
            // the current draft - the explicit clear must win.
            val existing = assignment(id = 1L, evaluatorScript = EvaluatorScript(ScriptType.KTS, "old"))
            whenever(assignmentService.findById(1L)).thenReturn(existing)
            whenever(assignmentService.save(any<Assignment>())).thenAnswer { it.arguments[0] as Assignment }

            service.saveAssignment(
                SaveAssignmentRequest(
                    id = 1L,
                    clearEvaluatorScript = true,
                    evaluatorScript = EvaluatorScript(ScriptType.KTS, "new draft"),
                ),
            )

            val saved = argumentCaptor<Assignment>()
            verify(assignmentService).save(saved.capture())
            assertNull(saved.firstValue.evaluatorScript)
        }

        @Test
        fun `evaluatorScript field replaces the existing one when clearEvaluatorScript is not set`() {
            val existing = assignment(id = 1L, evaluatorScript = EvaluatorScript(ScriptType.KTS, "old"))
            whenever(assignmentService.findById(1L)).thenReturn(existing)
            whenever(assignmentService.save(any<Assignment>())).thenAnswer { it.arguments[0] as Assignment }

            service.saveAssignment(
                SaveAssignmentRequest(id = 1L, evaluatorScript = EvaluatorScript(ScriptType.PYTHON, "new")),
            )

            val saved = argumentCaptor<Assignment>()
            verify(assignmentService).save(saved.capture())
            assertEquals(ScriptType.PYTHON, saved.firstValue.evaluatorScript?.type)
            assertEquals("new", saved.firstValue.evaluatorScript?.source)
        }

        @Test
        fun `null evaluatorScript without clearEvaluatorScript leaves the existing script untouched`() {
            val script = EvaluatorScript(ScriptType.KTS, "untouched")
            val existing = assignment(id = 1L, evaluatorScript = script)
            whenever(assignmentService.findById(1L)).thenReturn(existing)
            whenever(assignmentService.save(any<Assignment>())).thenAnswer { it.arguments[0] as Assignment }

            service.saveAssignment(SaveAssignmentRequest(id = 1L, name = "renamed"))

            val saved = argumentCaptor<Assignment>()
            verify(assignmentService).save(saved.capture())
            assertEquals(script, saved.firstValue.evaluatorScript)
        }
    }

    // --- getGitLabProjects (filter forks + deletions) -------------------

    @Test
    fun `getGitLabProjects filters out forks and marked-for-deletion projects`() {
        whenever(gitLabApiClient.listProjects(eq("lab"))).thenReturn(
            listOf(
                gitlabProject("root"), // keep
                gitlabProject("user1", forkedFrom = gitlabProject("root")), // drop: is a fork
                gitlabProject("doomed", deleted = "2026-01-01"), // drop: deletion-scheduled
            ),
        )

        val result = service.getGitLabProjects("lab")

        assertEquals(listOf("root/lab1"), result.map { it.pathWithNamespace })
    }

    // --- createForks: skip existing + per-fork error isolation ----------

    @Nested
    inner class CreateForks {

        @Test
        fun `pre-existing fork is reported as success and NOT re-forked (idempotency)`() {
            // Re-running for a partially-completed group used to surface every existing
            // fork as a 409 "already exists" error. We pre-load and skip them now.
            whenever(assignmentService.findById(1L)).thenReturn(assignment())
            whenever(gitlabUserService.findAll()).thenReturn(emptyList())
            whenever(gitLabApiClient.getProjectForks("course/lab1")).thenReturn(
                listOf(gitlabProject("alice")),
            )

            val res = service.createForks(
                CreateForksRequest(assignmentId = 1L, usernames = listOf("alice")),
            )

            val entry = (res.results ?: error("results must not be null")).single()
            assertEquals("alice", entry.username)
            assertEquals(true, entry.success)
            assertEquals("https://gitlab.example.com/alice/lab1", entry.forkUrl)
            // No actual fork call for 'alice' - that's the whole point of the skip.
            verify(gitLabApiClient, never()).forkProject(any(), eq("alice"))
        }

        @Test
        fun `unknown user is forked and the new fork URL is returned`() {
            whenever(assignmentService.findById(1L)).thenReturn(assignment())
            whenever(gitlabUserService.findAll()).thenReturn(emptyList())
            whenever(gitLabApiClient.getProjectForks("course/lab1")).thenReturn(emptyList())
            whenever(gitLabApiClient.forkProject("course/lab1", "bob"))
                .thenReturn(gitlabProject("bob"))

            val res = service.createForks(
                CreateForksRequest(assignmentId = 1L, usernames = listOf("bob")),
            )

            val entry = (res.results ?: error("results")).single()
            assertEquals(true, entry.success)
            assertEquals("https://gitlab.example.com/bob/lab1", entry.forkUrl)
            verify(gitLabApiClient).forkProject("course/lab1", "bob")
        }

        @Test
        fun `per-fork error does not abort the whole batch`() {
            whenever(assignmentService.findById(1L)).thenReturn(assignment())
            whenever(gitlabUserService.findAll()).thenReturn(emptyList())
            whenever(gitLabApiClient.getProjectForks("course/lab1")).thenReturn(emptyList())
            whenever(gitLabApiClient.forkProject("course/lab1", "boom"))
                .doThrow(RuntimeException("gitlab 500"))
            whenever(gitLabApiClient.forkProject("course/lab1", "ok"))
                .thenReturn(gitlabProject("ok"))

            val res = service.createForks(
                CreateForksRequest(assignmentId = 1L, usernames = listOf("boom", "ok")),
            )

            val results = res.results ?: error("results")
            assertEquals(2, results.size)
            val byName = results.associateBy { it.username }
            assertEquals(false, byName["boom"]?.success)
            assertEquals("gitlab 500", byName["boom"]?.error)
            assertEquals(true, byName["ok"]?.success)
        }

        @Test
        fun `missing assignmentId raises a clear error`() {
            assertThrows<IllegalStateException> {
                service.createForks(CreateForksRequest(assignmentId = null))
            }
            verify(gitLabApiClient, never()).forkProject(any(), any())
        }

        @Test
        fun `marked-for-deletion forks are not treated as existing`() {
            whenever(assignmentService.findById(1L)).thenReturn(assignment())
            whenever(gitlabUserService.findAll()).thenReturn(emptyList())
            whenever(gitLabApiClient.getProjectForks("course/lab1")).thenReturn(
                listOf(gitlabProject("alice", deleted = "2026-01-01")),
            )
            whenever(gitLabApiClient.forkProject("course/lab1", "alice"))
                .thenReturn(gitlabProject("alice"))

            service.createForks(
                CreateForksRequest(assignmentId = 1L, usernames = listOf("alice")),
            )

            // Pre-existing-but-deleted fork must NOT short-circuit the new fork.
            verify(gitLabApiClient).forkProject("course/lab1", "alice")
        }
    }
}
