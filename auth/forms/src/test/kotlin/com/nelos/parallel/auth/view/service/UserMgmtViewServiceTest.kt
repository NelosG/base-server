package com.nelos.parallel.auth.view.service

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.service.UserService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class UserMgmtViewServiceTest {

    private val userService: UserService = mock()
    private val userDetailsService: UserDetailsProviderService = mock()
    private val service = UserMgmtViewService(userService, userDetailsService)

    private fun user(id: Long, login: String, type: UserType): User = User().apply {
        this.id = id
        this.login = login
        this.type = type
    }

    @Test
    fun `getAdmins drops STUDENTs and keeps everyone else`() {
        whenever(userService.findAll()).thenReturn(
            listOf(
                user(1L, "admin", UserType.ADMIN),
                user(2L, "alice", UserType.STUDENT),
                user(3L, "ci-bot", UserType.USER),
            ),
        )

        val admins = service.getAdmins()

        assertEquals(setOf("admin", "ci-bot"), admins.map { it.login }.toSet())
        assertTrue(admins.none { it.login == "alice" })
    }

    @Test
    fun `createAdmin returns the plain raw password alongside the saved user`() {
        whenever(userDetailsService.createUserWithRandomPassword("bob", "Bob", UserType.ADMIN))
            .thenReturn(user(7L, "bob", UserType.ADMIN) to "raw-otp")

        val created = service.createAdmin("bob", "Bob")

        assertEquals(7L, created.id)
        assertEquals("bob", created.login)
        assertEquals("raw-otp", created.password)
    }

    @Test
    fun `resetPassword returns the freshly issued plain password`() {
        whenever(userDetailsService.resetPassword(5L)).thenReturn("new-otp")

        assertEquals("new-otp", service.resetPassword(5L))
    }

    @Test
    fun `deleteAdmin refuses the default 'admin' account`() {
        val ex = assertThrows<IllegalStateException> { service.deleteAdmin("admin") }
        assertTrue(ex.message?.contains("default admin") == true)
        verify(userService, never()).remove(any<User>())
        // We MUST NOT even look the user up - the guard is name-based and fires first.
        verify(userService, never()).findByLogin(any())
    }

    @Test
    fun `deleteAdmin refuses to delete a STUDENT - admins must use the students page`() {
        val student = user(2L, "alice", UserType.STUDENT)
        whenever(userService.findByLogin("alice")).thenReturn(student)

        val ex = assertThrows<IllegalStateException> { service.deleteAdmin("alice") }
        assertTrue(ex.message?.contains("students page") == true)
        verify(userService, never()).remove(any<User>())
    }

    @Test
    fun `deleteAdmin removes the user when name and type are admin-safe`() {
        val target = user(7L, "bob", UserType.ADMIN)
        whenever(userService.findByLogin("bob")).thenReturn(target)

        service.deleteAdmin("bob")

        verify(userService).remove(eq(target))
    }

    @Test
    fun `deleteAdmin errors when the user is not found`() {
        whenever(userService.findByLogin("ghost")).thenReturn(null)

        assertThrows<IllegalStateException> { service.deleteAdmin("ghost") }
    }
}
