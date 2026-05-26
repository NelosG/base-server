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

    private fun saveRequest(
        id: Long? = null,
        code: String = "lab1",
        name: String = "Lab 1",
        gitlabProjectPath: String = "course/lab1",
        testRepoUrl: String = "https://gitlab.example.com/tests/lab1.git",
        testRepoBranch: String = "main",
        description: String? = null,
        memoryLimitMb: Long? = null,
        threads: Int? = null,
        wallTimeSec: Int? = null,
        cpuTimeSec: Int? = null,
        maxProcesses: Int? = null,
        active: Boolean = true,
        evaluatorScript: EvaluatorScript? = null,
    ) = SaveAssignmentRequest(
        id = id, code = code, name = name, gitlabProjectPath = gitlabProjectPath,
        testRepoUrl = testRepoUrl, testRepoBranch = testRepoBranch, description = description,
        memoryLimitMb = memoryLimitMb, threads = threads, wallTimeSec = wallTimeSec,
        cpuTimeSec = cpuTimeSec, maxProcesses = maxProcesses, active = active,
        evaluatorScript = evaluatorScript,
    )

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

    // --- saveAssignment (PUT semantics) ---------------------------------

    @Nested
    inner class SaveAssignment {

        @Test
        fun `creates new assignment when id is null`() {
            whenever(assignmentService.save(any<Assignment>())).thenAnswer { it.arguments[0] as Assignment }

            service.saveAssignment(saveRequest(code = "lab2", name = "Lab 2", gitlabProjectPath = "c/lab2"))

            val saved = argumentCaptor<Assignment>()
            verify(assignmentService).save(saved.capture())
            assertEquals("lab2", saved.firstValue.code)
            assertEquals("Lab 2", saved.firstValue.name)
            // findById must NOT be called when id is null - fresh entity path.
            verify(assignmentService, never()).findById(any())
        }

        @Test
        fun `null numeric fields persist as null so the engine falls back to its defaults`() {
            // The bug this guards against: clearing the "Memory (MB)" input in
            // the UI used to keep the previous value because `data ?: existing`
            // discarded the explicit null. PUT semantics now overwrite.
            val existing = assignment(id = 1L)
            existing.memoryLimitMb = 4096L
            existing.threads = 8
            existing.wallTimeSec = 120
            existing.cpuTimeSec = 60
            existing.maxProcesses = 32
            whenever(assignmentService.findById(1L)).thenReturn(existing)
            whenever(assignmentService.save(any<Assignment>())).thenAnswer { it.arguments[0] as Assignment }

            service.saveAssignment(
                saveRequest(
                    id = 1L,
                    memoryLimitMb = null, threads = null,
                    wallTimeSec = null, cpuTimeSec = null, maxProcesses = null,
                ),
            )

            val saved = argumentCaptor<Assignment>()
            verify(assignmentService).save(saved.capture())
            assertNull(saved.firstValue.memoryLimitMb)
            assertNull(saved.firstValue.threads)
            assertNull(saved.firstValue.wallTimeSec)
            assertNull(saved.firstValue.cpuTimeSec)
            assertNull(saved.firstValue.maxProcesses)
        }

        @Test
        fun `non-null numeric fields are persisted as given`() {
            val existing = assignment(id = 1L)
            whenever(assignmentService.findById(1L)).thenReturn(existing)
            whenever(assignmentService.save(any<Assignment>())).thenAnswer { it.arguments[0] as Assignment }

            service.saveAssignment(
                saveRequest(id = 1L, memoryLimitMb = 2048L, threads = 4),
            )

            val saved = argumentCaptor<Assignment>()
            verify(assignmentService).save(saved.capture())
            assertEquals(2048L, saved.firstValue.memoryLimitMb)
            assertEquals(4, saved.firstValue.threads)
        }

        @Test
        fun `null evaluatorScript clears the existing one (PUT semantics)`() {
            val existing = assignment(id = 1L, evaluatorScript = EvaluatorScript(ScriptType.KTS, "old"))
            whenever(assignmentService.findById(1L)).thenReturn(existing)
            whenever(assignmentService.save(any<Assignment>())).thenAnswer { it.arguments[0] as Assignment }

            service.saveAssignment(saveRequest(id = 1L, evaluatorScript = null))

            val saved = argumentCaptor<Assignment>()
            verify(assignmentService).save(saved.capture())
            assertNull(saved.firstValue.evaluatorScript)
        }

        @Test
        fun `non-null evaluatorScript replaces the existing one`() {
            val existing = assignment(id = 1L, evaluatorScript = EvaluatorScript(ScriptType.KTS, "old"))
            whenever(assignmentService.findById(1L)).thenReturn(existing)
            whenever(assignmentService.save(any<Assignment>())).thenAnswer { it.arguments[0] as Assignment }

            service.saveAssignment(
                saveRequest(id = 1L, evaluatorScript = EvaluatorScript(ScriptType.PYTHON, "new")),
            )

            val saved = argumentCaptor<Assignment>()
            verify(assignmentService).save(saved.capture())
            assertEquals(ScriptType.PYTHON, saved.firstValue.evaluatorScript?.type)
            assertEquals("new", saved.firstValue.evaluatorScript?.source)
        }
    }

    // --- setAssignmentActive (partial toggle) ---------------------------

    @Nested
    inner class SetAssignmentActive {

        @Test
        fun `flips active flag and leaves every other field untouched`() {
            val existing = assignment(id = 1L)
            existing.memoryLimitMb = 2048L
            existing.threads = 4
            existing.active = true
            whenever(assignmentService.findById(1L)).thenReturn(existing)
            whenever(assignmentService.save(any<Assignment>())).thenAnswer { it.arguments[0] as Assignment }

            service.setAssignmentActive(1L, false)

            val saved = argumentCaptor<Assignment>()
            verify(assignmentService).save(saved.capture())
            // Only active flipped - the partial toggle must NOT trample the
            // resource-limit fields the way a full PUT would.
            assertEquals(false, saved.firstValue.active)
            assertEquals(2048L, saved.firstValue.memoryLimitMb)
            assertEquals(4, saved.firstValue.threads)
        }
    }

    // --- getGitLabProjects (filter forks + deletions) -------------------

    @Test
    fun `getGitLabProjects filters out forks and marked-for-deletion projects`() {
        // Service no longer forwards the search string to GitLab (?search= only
        // matches name, missing namespace-prefix queries). We fetch the full
        // candidate list and filter ourselves; the stub uses null to match.
        whenever(gitLabApiClient.listProjects(eq(null))).thenReturn(
            listOf(
                gitlabProject("root"), // keep
                gitlabProject("user1", forkedFrom = gitlabProject("root")), // drop: is a fork
                gitlabProject("doomed", deleted = "2026-01-01"), // drop: deletion-scheduled
            ),
        )

        val result = service.getGitLabProjects("lab")

        assertEquals(listOf("root/lab1"), result.map { it.pathWithNamespace })
    }

    @Test
    fun `getGitLabProjects filters by full path substring (namespace match)`() {
        whenever(gitLabApiClient.listProjects(eq(null))).thenReturn(
            listOf(
                gitlabProject("root"),        // "root/lab1"
                gitlabProject("other"),       // "other/lab1"
            ),
        )

        // "root" only appears in the namespace - GitLab's ?search= wouldn't
        // match this. We must.
        val result = service.getGitLabProjects("root")

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

        @Test
        fun `createForks resolves usernames from groupIds via gitlab mapping`() {
            // The UI can send either explicit usernames (legacy) or one/several
            // groupIds. When groupIds are present, the service walks each group's
            // members, looks up the gitlab name in GitlabUser, and forks against
            // those names. Users without a gitlab mapping silently drop.
            whenever(assignmentService.findById(1L)).thenReturn(assignment())
            whenever(memberService.findByGroupId(10L)).thenReturn(
                listOf(
                    com.nelos.parallel.pipeline.data.entity.StudentGroupMember()
                        .apply { groupId = 10L; userId = 1L },
                    com.nelos.parallel.pipeline.data.entity.StudentGroupMember()
                        .apply { groupId = 10L; userId = 2L },
                    com.nelos.parallel.pipeline.data.entity.StudentGroupMember()
                        .apply { groupId = 10L; userId = 3L }, // no gitlab mapping -> dropped
                ),
            )
            whenever(gitlabUserService.findAll()).thenReturn(
                listOf(
                    com.nelos.parallel.gitlab.entity.GitlabUser()
                        .apply { userId = 1L; gitLabName = "alice" },
                    com.nelos.parallel.gitlab.entity.GitlabUser()
                        .apply { userId = 2L; gitLabName = "bob" },
                ),
            )
            whenever(gitLabApiClient.getProjectForks("course/lab1")).thenReturn(emptyList())
            whenever(gitLabApiClient.forkProject(eq("course/lab1"), eq("alice")))
                .thenReturn(gitlabProject("alice"))
            whenever(gitLabApiClient.forkProject(eq("course/lab1"), eq("bob")))
                .thenReturn(gitlabProject("bob"))

            val res = service.createForks(CreateForksRequest(assignmentId = 1L, groupIds = listOf(10L)))

            val results = res.results ?: error("results")
            assertEquals(2, results.size)
            assertEquals(setOf("alice", "bob"), results.map { it.username }.toSet())
            verify(gitLabApiClient, never()).forkProject(any(), eq("user-3"))
        }

        @Test
        fun `createForks deduplicates usernames when one user belongs to multiple groups`() {
            whenever(assignmentService.findById(1L)).thenReturn(assignment())
            whenever(memberService.findByGroupId(10L)).thenReturn(
                listOf(com.nelos.parallel.pipeline.data.entity.StudentGroupMember()
                    .apply { groupId = 10L; userId = 1L }),
            )
            whenever(memberService.findByGroupId(20L)).thenReturn(
                listOf(com.nelos.parallel.pipeline.data.entity.StudentGroupMember()
                    .apply { groupId = 20L; userId = 1L }),
            )
            whenever(gitlabUserService.findAll()).thenReturn(
                listOf(com.nelos.parallel.gitlab.entity.GitlabUser()
                    .apply { userId = 1L; gitLabName = "alice" }),
            )
            whenever(gitLabApiClient.getProjectForks("course/lab1")).thenReturn(emptyList())
            whenever(gitLabApiClient.forkProject("course/lab1", "alice")).thenReturn(gitlabProject("alice"))

            val res = service.createForks(CreateForksRequest(assignmentId = 1L, groupIds = listOf(10L, 20L)))

            // The same user must not be forked twice across overlapping groups.
            assertEquals(1, (res.results ?: error("results")).size)
            verify(gitLabApiClient).forkProject("course/lab1", "alice")
        }

        @Test
        fun `createForks falls back to legacy single groupId field`() {
            whenever(assignmentService.findById(1L)).thenReturn(assignment())
            whenever(memberService.findByGroupId(99L)).thenReturn(
                listOf(com.nelos.parallel.pipeline.data.entity.StudentGroupMember()
                    .apply { groupId = 99L; userId = 1L }),
            )
            whenever(gitlabUserService.findAll()).thenReturn(
                listOf(com.nelos.parallel.gitlab.entity.GitlabUser()
                    .apply { userId = 1L; gitLabName = "alice" }),
            )
            whenever(gitLabApiClient.getProjectForks("course/lab1")).thenReturn(emptyList())
            whenever(gitLabApiClient.forkProject("course/lab1", "alice")).thenReturn(gitlabProject("alice"))

            service.createForks(CreateForksRequest(assignmentId = 1L, groupId = 99L))

            verify(memberService).findByGroupId(99L)
        }

        @Test
        fun `createForks errors when assignment has no GitLab project path`() {
            whenever(assignmentService.findById(1L)).thenReturn(
                assignment().apply { gitlabProjectPath = null },
            )

            assertThrows<IllegalStateException> {
                service.createForks(CreateForksRequest(assignmentId = 1L, usernames = listOf("a")))
            }
        }
    }

    // --- getGroupsWithForkStatus -----------------------------------------

    @Nested
    inner class GetGroupsWithForkStatus {

        @Test
        fun `marks each member as hasFork or unavailable depending on registry state`() {
            // Three students: alice has a fork, bob has a gitlab account but no
            // fork yet, charlie isn't even on gitlab (typo in profile?).
            whenever(assignmentService.findById(1L)).thenReturn(assignment())
            whenever(gitLabApiClient.getProjectForks("course/lab1")).thenReturn(
                listOf(gitlabProject("alice")),
            )
            whenever(gitLabApiClient.listUsers()).thenReturn(
                listOf(
                    com.nelos.parallel.gitlab.client.vo.GitLabUserInfo(username = "alice"),
                    com.nelos.parallel.gitlab.client.vo.GitLabUserInfo(username = "bob"),
                ),
            )
            val userA = com.nelos.parallel.auth.entity.User().apply { id = 1L; displayName = "Alice A." }
            val userB = com.nelos.parallel.auth.entity.User().apply { id = 2L; displayName = "Bob B." }
            val userC = com.nelos.parallel.auth.entity.User().apply { id = 3L; displayName = "Charlie C." }
            whenever(userService.findAll()).thenReturn(listOf(userA, userB, userC))
            whenever(gitlabUserService.findAll()).thenReturn(
                listOf(
                    com.nelos.parallel.gitlab.entity.GitlabUser().apply { userId = 1L; gitLabName = "alice" },
                    com.nelos.parallel.gitlab.entity.GitlabUser().apply { userId = 2L; gitLabName = "bob" },
                    com.nelos.parallel.gitlab.entity.GitlabUser().apply { userId = 3L; gitLabName = "charlie-typo" },
                ),
            )
            val group = com.nelos.parallel.pipeline.data.entity.StudentGroup().apply {
                id = 10L; name = "Group A"
            }
            whenever(groupService.findAll()).thenReturn(listOf(group))
            whenever(memberService.findByGroupId(10L)).thenReturn(
                listOf(
                    com.nelos.parallel.pipeline.data.entity.StudentGroupMember()
                        .apply { groupId = 10L; userId = 1L },
                    com.nelos.parallel.pipeline.data.entity.StudentGroupMember()
                        .apply { groupId = 10L; userId = 2L },
                    com.nelos.parallel.pipeline.data.entity.StudentGroupMember()
                        .apply { groupId = 10L; userId = 3L },
                ),
            )

            val statuses = service.getGroupsWithForkStatus(1L)

            assertEquals(1, statuses.size)
            val g = statuses.single()
            assertEquals(3, g.memberCount)
            assertEquals(1, g.missingForkCount)    // bob: on gitlab, no fork
            assertEquals(1, g.unavailableCount)    // charlie: not on gitlab
            val byName = (g.members ?: error("members")).associateBy { it.username }
            assertEquals(true, byName["alice"]?.hasFork)
            assertEquals(true, byName["bob"]?.gitlabExists)
            assertEquals(false, byName["bob"]?.hasFork)
            assertEquals(false, byName["charlie-typo"]?.gitlabExists)
        }

        @Test
        fun `member without a gitlab name maps to empty username and unavailable`() {
            // A user added to a group before their gitlab profile is set up -
            // the row still appears, but every status flag points to "not yet
            // ready". Forking would skip them.
            whenever(assignmentService.findById(1L)).thenReturn(assignment())
            whenever(gitLabApiClient.getProjectForks("course/lab1")).thenReturn(emptyList())
            whenever(gitLabApiClient.listUsers()).thenReturn(emptyList())
            whenever(userService.findAll()).thenReturn(
                listOf(com.nelos.parallel.auth.entity.User().apply { id = 1L; displayName = "X" }),
            )
            whenever(gitlabUserService.findAll()).thenReturn(emptyList())
            whenever(groupService.findAll()).thenReturn(
                listOf(com.nelos.parallel.pipeline.data.entity.StudentGroup()
                    .apply { id = 10L; name = "G" }),
            )
            whenever(memberService.findByGroupId(10L)).thenReturn(
                listOf(com.nelos.parallel.pipeline.data.entity.StudentGroupMember()
                    .apply { groupId = 10L; userId = 1L }),
            )

            val view = service.getGroupsWithForkStatus(1L).single()

            assertEquals("", view.members?.single()?.username)
            assertEquals(false, view.members?.single()?.gitlabExists)
        }

        @Test
        fun `errors when assignment has no GitLab project path`() {
            whenever(assignmentService.findById(1L)).thenReturn(
                assignment().apply { gitlabProjectPath = null },
            )
            assertThrows<IllegalStateException> { service.getGroupsWithForkStatus(1L) }
        }
    }

    // --- getExistingForks --------------------------------------------------

    @Test
    fun `getExistingForks filters out marked-for-deletion forks`() {
        whenever(assignmentService.findById(1L)).thenReturn(assignment())
        whenever(gitLabApiClient.getProjectForks("course/lab1")).thenReturn(
            listOf(
                gitlabProject("alive"),
                gitlabProject("doomed", deleted = "2026-01-01"),
            ),
        )

        val forks = service.getExistingForks(1L)

        assertEquals(listOf("alive/lab1"), forks.map { it.pathWithNamespace })
    }

    @Test
    fun `getExistingForks errors when assignment has no GitLab project path`() {
        whenever(assignmentService.findById(1L)).thenReturn(
            assignment().apply { gitlabProjectPath = null },
        )
        assertThrows<IllegalStateException> { service.getExistingForks(1L) }
    }
}
