package com.nelos.parallel.auth.view.service

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.entity.properties.UserProperties
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.auth.vo.ChangePasswordData
import com.nelos.parallel.auth.vo.UserData
import com.nelos.parallel.commons.security.AppRole
import org.junit.jupiter.api.*
import org.mockito.kotlin.*
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class UserViewServiceTest {

    private val userDetailsService: UserDetailsProviderService = mock()
    private val userService: UserService = mock()
    private val service = UserViewService(userDetailsService, userService)

    private val bcrypt = BCryptPasswordEncoder()

    @BeforeEach
    fun authenticate() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken("alice", null, AppRole.ROLE_USER)
    }

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    // --- getUserInfo ----------------------------------------------------

    @Test
    fun `getUserInfo strips the ROLE_ prefix and surfaces the password-change-required flag`() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken("alice", null, AppRole.ROLE_USER, AppRole.ROLE_ADMIN)
        whenever(userService.findByLogin("alice")).thenReturn(
            User().apply {
                login = "alice"
                displayName = "Alice"
                properties = UserProperties(passwordChangeRequired = true)
            },
        )

        val info = service.getUserInfo()

        assertEquals("alice", info.login)
        assertEquals("Alice", info.displayName)
        assertEquals(setOf("USER", "ADMIN"), info.roles.toSet())
        assertTrue(info.passwordChangeRequired)
    }

    // --- changePassword -------------------------------------------------

    @Nested
    inner class ChangePassword {

        private val currentHash = bcrypt.encode("oldPass1234")

        private fun stubLookup() {
            whenever(userDetailsService.loadUserByUsername("alice"))
                .thenReturn(UserData(1L, "alice", currentHash, UserType.USER))
        }

        @Test
        fun `delegates to UserDetailsProviderService on a valid change`() {
            stubLookup()

            service.changePassword(
                ChangePasswordData(currentPassword = "oldPass1234", newPassword = "newPass5678"),
            )

            verify(userDetailsService).changePassword(eq("alice"), eq("newPass5678"))
        }

        @Test
        fun `rejects missing currentPassword`() {
            val ex = assertThrows<IllegalStateException> {
                service.changePassword(ChangePasswordData(currentPassword = null, newPassword = "newPass5678"))
            }
            assertTrue(ex.message?.contains("currentPassword") == true)
            verify(userDetailsService, never()).changePassword(any(), any())
        }

        @Test
        fun `rejects missing newPassword`() {
            val ex = assertThrows<IllegalStateException> {
                service.changePassword(ChangePasswordData(currentPassword = "old", newPassword = null))
            }
            assertTrue(ex.message?.contains("newPassword") == true)
        }

        @Test
        fun `rejects newPassword shorter than 8 chars`() {
            val ex = assertThrows<IllegalArgumentException> {
                service.changePassword(ChangePasswordData("ok", "short"))
            }
            assertTrue(ex.message?.contains("8 characters") == true)
            verify(userDetailsService, never()).changePassword(any(), any())
        }

        @Test
        fun `rejects newPassword longer than 64 chars (bcrypt truncation guard)`() {
            val tooLong = "a".repeat(65)
            val ex = assertThrows<IllegalArgumentException> {
                service.changePassword(ChangePasswordData("ok-ok-ok-ok", tooLong))
            }
            assertTrue(ex.message?.contains("64 character") == true)
        }

        @Test
        fun `rejects when currentPassword does not match the stored hash`() {
            stubLookup()

            val ex = assertThrows<IllegalStateException> {
                service.changePassword(
                    ChangePasswordData("WRONG-old-pass", "newPass5678"),
                )
            }
            assertTrue(ex.message?.contains("Current password is incorrect") == true)
            verify(userDetailsService, never()).changePassword(any(), any())
        }
    }

    // --- changeDisplayName ----------------------------------------------

    @Nested
    inner class ChangeDisplayName {

        @Test
        fun `rejects blank display name`() {
            assertThrows<IllegalArgumentException> { service.changeDisplayName("   ") }
            verify(userService, never()).save(any<User>())
        }

        @Test
        fun `saves the new display name onto the locked user row`() {
            val u = User().apply { id = 1L; login = "alice"; displayName = "old" }
            whenever(userService.findByLoginForUpdate("alice")).thenReturn(u)

            service.changeDisplayName("Alice Anderson")

            val saved = argumentCaptor<User>()
            verify(userService).save(saved.capture())
            assertEquals("Alice Anderson", saved.firstValue.displayName)
        }

        @Test
        fun `errors when the current user disappeared between auth and lookup`() {
            whenever(userService.findByLoginForUpdate("alice")).thenReturn(null)

            assertThrows<IllegalStateException> { service.changeDisplayName("ok") }
        }
    }
}
