package com.nelos.parallel.gitlab.forms

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.entity.properties.UserProperties
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.gitlab.entity.GitlabUser
import com.nelos.parallel.gitlab.forms.vo.CreateStudentRequest
import com.nelos.parallel.gitlab.service.GitlabUserService
import com.nelos.parallel.pipeline.data.entity.StudentGroupMember
import com.nelos.parallel.pipeline.data.service.StudentGroupMemberService
import com.nelos.parallel.pipeline.data.service.StudentGroupService
import com.nelos.parallel.pipeline.data.service.SubmissionService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class StudentViewServiceTest {

    private val userService: UserService = mock()
    private val userDetailsService: UserDetailsProviderService = mock()
    private val gitlabUserService: GitlabUserService = mock()
    private val groupService: StudentGroupService = mock()
    private val groupMemberService: StudentGroupMemberService = mock()
    private val submissionService: SubmissionService = mock()

    private val service = StudentViewService(
        userService, userDetailsService, gitlabUserService,
        groupService, groupMemberService, submissionService,
    )

    // --- builders --------------------------------------------------------

    private fun student(id: Long, login: String = "stu$id"): User = User().apply {
        this.id = id
        this.login = login
        this.type = UserType.STUDENT
    }

    private fun nonStudent(id: Long, type: UserType = UserType.ADMIN): User = User().apply {
        this.id = id
        this.login = "user$id"
        this.type = type
    }

    private fun gitlabLink(userId: Long, name: String): GitlabUser = GitlabUser().apply {
        this.userId = userId
        this.gitLabName = name
    }

    private fun member(userId: Long, groupId: Long): StudentGroupMember = StudentGroupMember().apply {
        this.userId = userId
        this.groupId = groupId
    }

    private fun stubEmptyReads() {
        whenever(gitlabUserService.findAll()).thenReturn(emptyList())
        whenever(groupMemberService.findAll()).thenReturn(emptyList())
        whenever(groupService.findAll()).thenReturn(emptyList())
        whenever(submissionService.findAll()).thenReturn(emptyList())
    }

    // --- getStudents (filtering) ----------------------------------------

    @Nested
    inner class GetStudents {

        @Test
        fun `null groupId returns all STUDENTs (and only STUDENTs)`() {
            whenever(userService.findAll()).thenReturn(
                listOf(student(ALICE_ID), nonStudent(2L), student(BOB_ID), nonStudent(4L, UserType.USER)),
            )
            stubEmptyReads()

            val result = service.getStudents(groupId = null)

            assertEquals(listOf(ALICE_ID, BOB_ID), result.map { it.id })
        }

        @Test
        fun `STUDENTS_WITHOUT_GROUP sentinel returns students with no group membership`() {
            whenever(userService.findAll()).thenReturn(
                listOf(student(ALICE_ID), student(BOB_ID), student(CAROL_ID)),
            )
            // Only the middle student is in any group.
            whenever(groupMemberService.findAll()).thenReturn(listOf(member(BOB_ID, GROUP_ID)))
            whenever(gitlabUserService.findAll()).thenReturn(emptyList())
            whenever(groupService.findAll()).thenReturn(emptyList())
            whenever(submissionService.findAll()).thenReturn(emptyList())

            val result = service.getStudents(StudentViewService.STUDENTS_WITHOUT_GROUP)

            assertEquals(setOf(ALICE_ID, CAROL_ID), result.map { it.id }.toSet())
        }

        @Test
        fun `concrete group id returns only the members of that group`() {
            val otherGroupId = 200L
            whenever(userService.findAll()).thenReturn(
                listOf(student(ALICE_ID), student(BOB_ID), student(CAROL_ID)),
            )
            whenever(groupMemberService.findAll()).thenReturn(
                listOf(
                    member(ALICE_ID, GROUP_ID),
                    member(BOB_ID, otherGroupId),
                    member(CAROL_ID, GROUP_ID),
                ),
            )
            whenever(gitlabUserService.findAll()).thenReturn(emptyList())
            whenever(groupService.findAll()).thenReturn(emptyList())
            whenever(submissionService.findAll()).thenReturn(emptyList())

            val result = service.getStudents(groupId = GROUP_ID)

            assertEquals(setOf(ALICE_ID, CAROL_ID), result.map { it.id }.toSet())
        }

        @Test
        fun `password status reflects whether an OTP initial password is still on file`() {
            val s = student(ALICE_ID).apply {
                properties = UserProperties(
                    initialPassword = "raw-otp",
                    passwordChangeRequired = true,
                )
            }
            whenever(userService.findAll()).thenReturn(listOf(s))
            stubEmptyReads()

            val result = service.getStudents(null)

            assertEquals("INITIAL", result.single().passwordStatus)
            assertEquals("raw-otp", result.single().initialPassword)
        }
    }

    // --- getStudent (type guard) -----------------------------------------

    @Nested
    inner class GetStudent {

        @Test
        fun `unknown id errors with a clear message`() {
            val unknownId = 99L
            whenever(userService.tryFindById(unknownId)).thenReturn(null)

            val ex = assertThrows<IllegalStateException> { service.getStudent(unknownId) }
            assertEquals(true, ex.message?.contains("$unknownId"))
        }

        @Test
        fun `non-student user is refused - protects against admins leaking through the students page`() {
            val adminId = 7L
            whenever(userService.tryFindById(adminId)).thenReturn(nonStudent(adminId))

            assertThrows<IllegalStateException> { service.getStudent(adminId) }
        }
    }

    // --- createStudent --------------------------------------------------

    @Nested
    inner class CreateStudent {

        @Test
        fun `blank login is rejected before any service call`() {
            assertThrows<IllegalStateException> {
                service.createStudent(CreateStudentRequest(login = "   "))
            }
            verify(userDetailsService, never()).createUserWithRandomPassword(any(), any(), any())
        }

        @Test
        fun `creates the user and skips gitlab linkage when name is blank`() {
            whenever(userDetailsService.createUserWithRandomPassword(eq("alice"), anyOrNull(), eq(UserType.STUDENT)))
                .thenReturn(student(NEW_STUDENT_ID, "alice") to "otp")
            whenever(userService.tryFindById(NEW_STUDENT_ID)).thenReturn(student(NEW_STUDENT_ID, "alice"))
            stubEmptyReads()

            service.createStudent(CreateStudentRequest(login = "alice", gitlabName = "  "))

            verify(gitlabUserService, never()).save(any<GitlabUser>())
        }

        @Test
        fun `creates the user and attaches gitlab + group when both are provided`() {
            whenever(userDetailsService.createUserWithRandomPassword(eq("alice"), anyOrNull(), eq(UserType.STUDENT)))
                .thenReturn(student(NEW_STUDENT_ID, "alice") to "otp")
            whenever(userService.tryFindById(NEW_STUDENT_ID)).thenReturn(student(NEW_STUDENT_ID, "alice"))
            stubEmptyReads()

            service.createStudent(
                CreateStudentRequest(login = "alice", gitlabName = "  alice-gl  ", groupId = GROUP_ID),
            )

            // Gitlab link saved with trimmed name.
            val gl = argumentCaptor<GitlabUser>()
            verify(gitlabUserService).save(gl.capture())
            assertEquals("alice-gl", gl.firstValue.gitLabName)
            assertEquals(NEW_STUDENT_ID, gl.firstValue.userId)
            // Group member saved.
            val mem = argumentCaptor<StudentGroupMember>()
            verify(groupMemberService).save(mem.capture())
            assertEquals(GROUP_ID, mem.firstValue.groupId)
            assertEquals(NEW_STUDENT_ID, mem.firstValue.userId)
        }
    }

    // --- resetPassword -------------------------------------------------

    @Test
    fun `resetPassword refuses to operate on a non-student user`() {
        val adminId = 2L
        whenever(userService.tryFindById(adminId)).thenReturn(nonStudent(adminId))

        assertThrows<IllegalStateException> { service.resetPassword(adminId) }
        verify(userDetailsService, never()).resetPassword(any())
    }

    @Test
    fun `resetPassword delegates to UserDetailsProviderService for a real student`() {
        whenever(userService.tryFindById(ALICE_ID)).thenReturn(student(ALICE_ID))
        whenever(userDetailsService.resetPassword(ALICE_ID)).thenReturn("new-otp")

        assertEquals("new-otp", service.resetPassword(ALICE_ID))
    }

    // --- updateStudent (gitlab link CRUD) -------------------------------

    @Nested
    inner class UpdateStudent {

        @Test
        fun `non-student is refused`() {
            val adminId = 2L
            whenever(userService.tryFindById(adminId)).thenReturn(nonStudent(adminId))

            assertThrows<IllegalStateException> {
                service.updateStudent(adminId, displayName = "x", gitlabName = null)
            }
        }

        @Test
        fun `blank displayName performs no save (no-op update)`() {
            whenever(userService.tryFindById(ALICE_ID))
                .thenReturn(student(ALICE_ID, "alice").apply { displayName = "Old" })
            stubEmptyReads()

            service.updateStudent(ALICE_ID, displayName = "   ", gitlabName = null)

            // Bug fix: blank displayName must not issue an UPDATE for an unchanged row.
            verify(userService, never()).save(any<User>())
        }

        @Test
        fun `displayName equal to stored value performs no save`() {
            whenever(userService.tryFindById(ALICE_ID))
                .thenReturn(student(ALICE_ID, "alice").apply { displayName = "Alice" })
            stubEmptyReads()

            service.updateStudent(ALICE_ID, displayName = "  Alice  ", gitlabName = null)

            verify(userService, never()).save(any<User>())
        }

        @Test
        fun `genuinely changed displayName is persisted (trimmed)`() {
            whenever(userService.tryFindById(ALICE_ID))
                .thenReturn(student(ALICE_ID, "alice").apply { displayName = "Old" })
            stubEmptyReads()

            service.updateStudent(ALICE_ID, displayName = "  New  ", gitlabName = null)

            val saved = argumentCaptor<User>()
            verify(userService).save(saved.capture())
            assertEquals("New", saved.firstValue.displayName)
        }

        @Test
        fun `empty gitlab name removes the link if one exists`() {
            whenever(userService.tryFindById(ALICE_ID)).thenReturn(student(ALICE_ID))
            whenever(gitlabUserService.findByUserId(ALICE_ID)).thenReturn(gitlabLink(ALICE_ID, "old"))
            stubEmptyReads()

            service.updateStudent(ALICE_ID, displayName = null, gitlabName = "")

            verify(gitlabUserService).remove(any<GitlabUser>())
            verify(gitlabUserService, never()).save(any<GitlabUser>())
        }

        @Test
        fun `setting a gitlab name when none exists creates a new link`() {
            whenever(userService.tryFindById(ALICE_ID)).thenReturn(student(ALICE_ID))
            whenever(gitlabUserService.findByUserId(ALICE_ID)).thenReturn(null)
            stubEmptyReads()

            service.updateStudent(ALICE_ID, displayName = null, gitlabName = "alice-gl")

            val saved = argumentCaptor<GitlabUser>()
            verify(gitlabUserService).save(saved.capture())
            assertEquals(ALICE_ID, saved.firstValue.userId)
            assertEquals("alice-gl", saved.firstValue.gitLabName)
        }

        @Test
        fun `setting a gitlab name when one exists updates it instead of duplicating`() {
            whenever(userService.tryFindById(ALICE_ID)).thenReturn(student(ALICE_ID))
            val existing = gitlabLink(ALICE_ID, "old-name")
            whenever(gitlabUserService.findByUserId(ALICE_ID)).thenReturn(existing)
            stubEmptyReads()

            service.updateStudent(ALICE_ID, displayName = null, gitlabName = "  new-name  ")

            val saved = argumentCaptor<GitlabUser>()
            verify(gitlabUserService).save(saved.capture())
            assertEquals("new-name", saved.firstValue.gitLabName)
            // Important: we saved the SAME instance, not a new one.
            assertSame(existing, saved.firstValue)
        }
    }

    // --- group ops (idempotent) -----------------------------------------

    @Nested
    inner class GroupOps {

        @Test
        fun `addToGroup is idempotent - already-member call is a no-op`() {
            whenever(groupMemberService.findByGroupAndUser(GROUP_ID, ALICE_ID))
                .thenReturn(member(ALICE_ID, GROUP_ID))

            service.addToGroup(userId = ALICE_ID, groupId = GROUP_ID)

            verify(groupMemberService, never()).save(any<StudentGroupMember>())
        }

        @Test
        fun `addToGroup inserts when the membership did not exist`() {
            whenever(groupMemberService.findByGroupAndUser(GROUP_ID, ALICE_ID)).thenReturn(null)

            service.addToGroup(userId = ALICE_ID, groupId = GROUP_ID)

            verify(groupMemberService).save(any<StudentGroupMember>())
        }

        @Test
        fun `removeFromGroup tolerates an absent membership`() {
            whenever(groupMemberService.findByGroupAndUser(GROUP_ID, ALICE_ID)).thenReturn(null)

            service.removeFromGroup(userId = ALICE_ID, groupId = GROUP_ID)

            verify(groupMemberService, never()).remove(any<StudentGroupMember>())
        }

        @Test
        fun `removeFromGroup deletes the existing membership row`() {
            val existing = member(ALICE_ID, GROUP_ID)
            whenever(groupMemberService.findByGroupAndUser(GROUP_ID, ALICE_ID)).thenReturn(existing)

            service.removeFromGroup(userId = ALICE_ID, groupId = GROUP_ID)

            verify(groupMemberService).remove(eq(existing))
        }
    }

    // --- deleteStudent --------------------------------------------------

    @Test
    fun `deleteStudent refuses to operate on a non-student row`() {
        val adminId = 5L
        whenever(userService.tryFindById(adminId)).thenReturn(nonStudent(adminId))

        assertThrows<IllegalStateException> { service.deleteStudent(adminId) }
        verify(userService, never()).remove(any<User>())
    }

    @Test
    fun `deleteStudent removes the student entity`() {
        val s = student(ALICE_ID)
        whenever(userService.tryFindById(ALICE_ID)).thenReturn(s)

        service.deleteStudent(ALICE_ID)

        verify(userService).remove(eq(s))
    }

    companion object {
        private const val ALICE_ID = 1L
        private const val BOB_ID = 2L
        private const val CAROL_ID = 3L
        private const val NEW_STUDENT_ID = 11L
        private const val GROUP_ID = 100L
    }
}
