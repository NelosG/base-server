package com.nelos.parallel.gitlab.forms

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.gitlab.forms.vo.SaveStudentGroupRequest
import com.nelos.parallel.gitlab.forms.vo.StudentGroupMemberView
import com.nelos.parallel.gitlab.integration.GitlabStudentResolver
import com.nelos.parallel.gitlab.service.GitlabUserService
import com.nelos.parallel.pipeline.data.entity.StudentGroup
import com.nelos.parallel.pipeline.data.entity.StudentGroupMember
import com.nelos.parallel.pipeline.data.service.StudentGroupMemberService
import com.nelos.parallel.pipeline.data.service.StudentGroupService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class StudentGroupViewServiceTest {

    private val groupService: StudentGroupService = mock()
    private val memberService: StudentGroupMemberService = mock()
    private val userService: UserService = mock()
    private val gitlabUserService: GitlabUserService = mock()
    private val studentResolver: GitlabStudentResolver = mock()

    private val service = StudentGroupViewService(
        groupService, memberService, userService, gitlabUserService, studentResolver,
    )

    private fun user(id: Long, login: String = "u$id", displayName: String? = null): User =
        User().apply {
            this.id = id
            this.login = login
            this.displayName = displayName
        }

    private fun group(id: Long, name: String = "G$id"): StudentGroup = StudentGroup().apply {
        this.id = id
        this.name = name
    }

    private fun stubEmptyCtx() {
        whenever(userService.findAll()).thenReturn(emptyList())
        whenever(gitlabUserService.findAll()).thenReturn(emptyList())
    }

    // --- saveGroup / member resolution ----------------------------------

    @Nested
    inner class SaveGroup {

        @Test
        fun `replaces the whole member set when members list is provided`() {
            // The contract is "explicit member list = full replace". Spell out the
            // delete-then-insert flow so the admin UI's overwrite behaviour stays sane.
            whenever(groupService.save(any<StudentGroup>())).thenAnswer {
                (it.arguments[0] as StudentGroup).apply {
                    id = 5L
                }
            }
            whenever(groupService.findById(5L)).thenReturn(group(5L))
            whenever(userService.tryFindById(11L)).thenReturn(user(11L))
            whenever(memberService.findByGroupAndUser(5L, 11L)).thenReturn(null)
            stubEmptyCtx()
            whenever(memberService.findByGroupId(5L)).thenReturn(emptyList())

            service.saveGroup(
                SaveStudentGroupRequest(
                    name = "Group A",
                    members = listOf(StudentGroupMemberView(userId = 11L)),
                ),
            )

            verify(memberService).deleteByGroupId(5L)
            verify(memberService).save(any<StudentGroupMember>())
        }

        @Test
        fun `null members list leaves the existing memberships untouched`() {
            whenever(groupService.findById(5L)).thenReturn(group(5L))
            whenever(groupService.save(any<StudentGroup>())).thenAnswer { it.arguments[0] }
            stubEmptyCtx()
            whenever(memberService.findByGroupId(5L)).thenReturn(emptyList())

            service.saveGroup(SaveStudentGroupRequest(id = 5L, name = "renamed", members = null))

            verify(memberService, never()).deleteByGroupId(any())
            verify(memberService, never()).save(any<StudentGroupMember>())
        }
    }

    // --- addMember (private, exercised through saveGroup) ---------------

    @Nested
    inner class MemberResolution {

        private fun saveWithMember(view: StudentGroupMemberView) {
            whenever(groupService.save(any<StudentGroup>())).thenAnswer {
                (it.arguments[0] as StudentGroup).apply {
                    id = 5L
                }
            }
            whenever(groupService.findById(5L)).thenReturn(group(5L))
            stubEmptyCtx()
            whenever(memberService.findByGroupId(5L)).thenReturn(emptyList())

            service.saveGroup(
                SaveStudentGroupRequest(name = "g", members = listOf(view)),
            )
        }

        @Test
        fun `prefers explicit userId over gitlabName when both are present`() {
            whenever(userService.tryFindById(11L)).thenReturn(user(11L))
            whenever(memberService.findByGroupAndUser(5L, 11L)).thenReturn(null)

            saveWithMember(StudentGroupMemberView(userId = 11L, gitlabName = "ignored"))

            verify(studentResolver, never()).resolveOrAutoCreate(any())
            verify(memberService).save(any<StudentGroupMember>())
        }

        @Test
        fun `falls back to gitlabName when userId is missing`() {
            whenever(studentResolver.resolveOrAutoCreate("alice")).thenReturn(11L)
            whenever(userService.tryFindById(11L)).thenReturn(user(11L))
            whenever(memberService.findByGroupAndUser(5L, 11L)).thenReturn(null)

            saveWithMember(StudentGroupMemberView(userId = null, gitlabName = "alice"))

            verify(studentResolver).resolveOrAutoCreate("alice")
            verify(memberService).save(any<StudentGroupMember>())
        }

        @Test
        fun `falls back to login when both userId and gitlabName are missing`() {
            whenever(studentResolver.resolveOrAutoCreate("alice")).thenReturn(11L)
            whenever(userService.tryFindById(11L)).thenReturn(user(11L))
            whenever(memberService.findByGroupAndUser(5L, 11L)).thenReturn(null)

            saveWithMember(StudentGroupMemberView(userId = null, gitlabName = null, login = "alice"))

            verify(studentResolver).resolveOrAutoCreate("alice")
        }

        @Test
        fun `member with no identifying field is silently skipped`() {
            saveWithMember(StudentGroupMemberView(userId = null, gitlabName = null, login = null))

            verify(memberService, never()).save(any<StudentGroupMember>())
            verify(studentResolver, never()).resolveOrAutoCreate(any())
        }

        @Test
        fun `blank gitlabName is treated as missing (the trim() check)`() {
            whenever(studentResolver.resolveOrAutoCreate("alice")).thenReturn(11L)
            whenever(userService.tryFindById(11L)).thenReturn(user(11L))
            whenever(memberService.findByGroupAndUser(5L, 11L)).thenReturn(null)

            saveWithMember(StudentGroupMemberView(gitlabName = "   ", login = "alice"))

            verify(studentResolver).resolveOrAutoCreate("alice")
        }

        @Test
        fun `displayName is updated only when it actually differs from the stored value`() {
            // No save() should happen when displayName matches the existing one,
            // even though we're walking the resolveUser -> user code path.
            whenever(userService.tryFindById(11L)).thenReturn(user(11L, displayName = "Alice"))
            whenever(memberService.findByGroupAndUser(5L, 11L)).thenReturn(null)

            saveWithMember(StudentGroupMemberView(userId = 11L, displayName = "Alice"))

            // userService.save NOT called for the user - only memberService.save for membership.
            verify(userService, never()).save(any<User>())
            verify(memberService).save(any<StudentGroupMember>())
        }

        @Test
        fun `displayName change persists when it really differs`() {
            whenever(userService.tryFindById(11L)).thenReturn(user(11L, displayName = "Old"))
            whenever(memberService.findByGroupAndUser(5L, 11L)).thenReturn(null)

            saveWithMember(StudentGroupMemberView(userId = 11L, displayName = "  New  "))

            val saved = argumentCaptor<User>()
            verify(userService).save(saved.capture())
            assertEquals("New", saved.firstValue.displayName)
        }

        @Test
        fun `idempotent membership - already-member call does not insert a duplicate row`() {
            whenever(userService.tryFindById(11L)).thenReturn(user(11L))
            whenever(memberService.findByGroupAndUser(5L, 11L))
                .thenReturn(StudentGroupMember().apply { userId = 11L; groupId = 5L })

            saveWithMember(StudentGroupMemberView(userId = 11L))

            verify(memberService, never()).save(any<StudentGroupMember>())
        }
    }
}
