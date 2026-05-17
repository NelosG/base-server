package com.nelos.parallel.gitlab.integration

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.gitlab.entity.GitlabUser
import com.nelos.parallel.gitlab.service.GitlabUserService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import kotlin.test.assertEquals

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class GitlabStudentResolverTest {

    private val userService: UserService = mock()
    private val gitlabUserService: GitlabUserService = mock()
    private val userDetailsService: UserDetailsProviderService = mock()
    private val resolver = GitlabStudentResolver(userService, gitlabUserService, userDetailsService)

    private fun user(id: Long = 11L, login: String = "alice"): User = User().apply {
        this.id = id
        this.login = login
        this.type = UserType.STUDENT
    }

    private fun gitlabUser(userId: Long = 11L, gitlabName: String = "alice"): GitlabUser =
        GitlabUser().apply {
            this.userId = userId
            this.gitLabName = gitlabName
        }

    @Test
    fun `step 1 - existing GitlabUser mapping returns the linked user without creating anything`() {
        whenever(gitlabUserService.findByGitlabName("alice")).thenReturn(gitlabUser())
        whenever(userService.tryFindById(11L)).thenReturn(user())

        val resolved = resolver.resolveOrAutoCreate("alice")

        assertEquals(11L, resolved)
        verify(userService, never()).findByLogin(any())
        verify(userDetailsService, never()).createUserWithRandomPassword(any(), any(), any())
    }

    @Test
    fun `step 2 - no mapping, but a user with matching login already exists, attaches a fresh mapping`() {
        whenever(gitlabUserService.findByGitlabName("alice")).thenReturn(null)
        whenever(userService.findByLogin("alice")).thenReturn(user())

        val resolved = resolver.resolveOrAutoCreate("alice")

        assertEquals(11L, resolved)
        // Mapping must have been saved.
        val captor = argumentCaptor<GitlabUser>()
        verify(gitlabUserService).save(captor.capture())
        assertEquals(11L, captor.firstValue.userId)
        assertEquals("alice", captor.firstValue.gitLabName)
        verify(userDetailsService, never()).createUserWithRandomPassword(any(), any(), any())
    }

    @Test
    fun `step 3 - nothing matches, creates a fresh STUDENT and attaches a mapping`() {
        whenever(gitlabUserService.findByGitlabName("bob")).thenReturn(null)
        whenever(userService.findByLogin("bob")).thenReturn(null)
        val freshUser = user(id = 99L, login = "bob")
        whenever(userDetailsService.createUserWithRandomPassword("bob", "bob", UserType.STUDENT))
            .thenReturn(freshUser to "raw-pwd-once")

        val resolved = resolver.resolveOrAutoCreate("bob")

        assertEquals(99L, resolved)
        val captor = argumentCaptor<GitlabUser>()
        verify(gitlabUserService).save(captor.capture())
        assertEquals(99L, captor.firstValue.userId)
        assertEquals("bob", captor.firstValue.gitLabName)
        verify(userDetailsService).createUserWithRandomPassword(eq("bob"), eq("bob"), eq(UserType.STUDENT))
    }

    @Test
    fun `orphan mapping (referenced user no longer exists) is an error`() {
        whenever(gitlabUserService.findByGitlabName("ghost")).thenReturn(
            gitlabUser(
                userId = 404L,
                gitlabName = "ghost"
            )
        )
        whenever(userService.tryFindById(404L)).thenReturn(null)

        val ex = assertThrows<IllegalStateException> { resolver.resolveOrAutoCreate("ghost") }
        assertEquals(true, ex.message?.contains("404"))
    }

    @Test
    fun `mapping with null userId is an error`() {
        whenever(gitlabUserService.findByGitlabName("weird")).thenReturn(
            GitlabUser().apply { gitLabName = "weird" /* userId stays null */ },
        )

        assertThrows<IllegalStateException> { resolver.resolveOrAutoCreate("weird") }
    }
}
