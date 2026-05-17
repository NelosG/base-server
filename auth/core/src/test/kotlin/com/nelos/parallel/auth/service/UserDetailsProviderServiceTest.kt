package com.nelos.parallel.auth.service

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.entity.properties.UserProperties
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.exceptions.UserAlreadyExistsException
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class UserDetailsProviderServiceTest {

    private val users: UserService = mock()
    private val service = UserDetailsProviderService(users)
    private val bcrypt = BCryptPasswordEncoder()

    private fun user(
        id: Long? = 1L,
        login: String? = "alice",
        encryptedPassword: String? = bcrypt.encode("pw"),
        type: UserType? = UserType.STUDENT,
        properties: UserProperties? = null,
    ): User = User().apply {
        this.id = id
        this.login = login
        this.encryptedPassword = encryptedPassword
        this.type = type
        this.properties = properties
    }

    // --- loadUserByUsername ---------------------------------------------

    @Nested
    inner class LoadUserByUsername {

        @Test
        fun `returns UserData copy of the found entity`() {
            whenever(users.findByLogin("alice")).thenReturn(user(id = 11L, login = "alice", type = UserType.STUDENT))

            val data = service.loadUserByUsername("alice")

            assertEquals(11L, data.id)
            assertEquals("alice", data.login)
            assertEquals(UserType.STUDENT, data.type)
        }

        @Test
        fun `unknown login throws UsernameNotFoundException`() {
            whenever(users.findByLogin(any())).thenReturn(null)

            assertThrows<UsernameNotFoundException> { service.loadUserByUsername("nope") }
        }

        @Test
        fun `user with null id is rejected (data invariant violation)`() {
            whenever(users.findByLogin("alice")).thenReturn(user(id = null))

            assertThrows<IllegalStateException> { service.loadUserByUsername("alice") }
        }
    }

    // --- createUserWithRandomPassword -----------------------------------

    @Nested
    inner class CreateUserWithRandomPassword {

        @Test
        fun `existing login is rejected before any save`() {
            whenever(users.findByLogin("bob")).thenReturn(user(login = "bob"))

            assertThrows<UserAlreadyExistsException> {
                service.createUserWithRandomPassword("bob", "Bob", UserType.ADMIN)
            }
            verify(users, never()).save(any<User>())
        }

        @Test
        fun `creates a user with bcrypt-hashed password, OTP plain stored once, change-required flag set`() {
            whenever(users.findByLogin("bob")).thenReturn(null)
            whenever(users.save(any<User>())).thenAnswer { it.arguments[0] as User }

            val (saved, raw) = service.createUserWithRandomPassword("bob", "Bob", UserType.ADMIN)

            assertEquals("bob", saved.login)
            assertEquals("Bob", saved.displayName)
            assertEquals(UserType.ADMIN, saved.type)
            // Hash matches the raw password.
            assertTrue(bcrypt.matches(raw, saved.encryptedPassword))
            // Plain raw saved once for the admin to display, change-required flag armed.
            assertEquals(raw, saved.properties?.initialPassword)
            assertEquals(true, saved.properties?.passwordChangeRequired)
        }

        @Test
        fun `null displayName falls back to login (so admin UI never shows a blank cell)`() {
            whenever(users.findByLogin("bob")).thenReturn(null)
            whenever(users.save(any<User>())).thenAnswer { it.arguments[0] as User }

            val (saved, _) = service.createUserWithRandomPassword("bob", null, UserType.STUDENT)

            assertEquals("bob", saved.displayName)
        }

        @Test
        fun `raw password is 16 chars from the alphanumeric alphabet (matches RandomPasswordGenerator contract)`() {
            whenever(users.findByLogin(any())).thenReturn(null)
            whenever(users.save(any<User>())).thenAnswer { it.arguments[0] as User }

            val (_, raw) = service.createUserWithRandomPassword("bob", "Bob", UserType.ADMIN)

            assertEquals(16, raw.length)
            val allowed = ('A'..'Z') + ('a'..'z') + ('0'..'9')
            assertTrue(raw.all { it in allowed })
        }
    }

    // --- resetPassword (admin re-arms OTP) ------------------------------

    @Nested
    inner class ResetPassword {

        @Test
        fun `unknown user id throws UsernameNotFoundException`() {
            whenever(users.tryFindByIdForUpdate(99L)).thenReturn(null)

            assertThrows<UsernameNotFoundException> { service.resetPassword(99L) }
        }

        @Test
        fun `re-arms the OTP flow and replaces the bcrypt hash`() {
            val target = user(id = 5L, encryptedPassword = bcrypt.encode("oldPass"))
            whenever(users.tryFindByIdForUpdate(5L)).thenReturn(target)
            whenever(users.save(any<User>())).thenAnswer { it.arguments[0] as User }

            val raw = service.resetPassword(5L)

            val saved = argumentCaptor<User>()
            verify(users).save(saved.capture())
            assertTrue(bcrypt.matches(raw, saved.firstValue.encryptedPassword))
            assertEquals(raw, saved.firstValue.properties?.initialPassword)
            assertEquals(true, saved.firstValue.properties?.passwordChangeRequired)
        }

        @Test
        fun `keeps the existing UserProperties instance if one was already attached`() {
            val props = UserProperties(initialPassword = "old-otp", passwordChangeRequired = false)
            val target = user(id = 5L).apply { properties = props }
            whenever(users.tryFindByIdForUpdate(5L)).thenReturn(target)
            whenever(users.save(any<User>())).thenAnswer { it.arguments[0] as User }

            service.resetPassword(5L)

            val saved = argumentCaptor<User>()
            verify(users).save(saved.capture())
            // Mutates the same `properties` object instead of replacing it.
            assertEquals(props, saved.firstValue.properties)
        }
    }

    // --- changePassword (user-driven, clears OTP) -----------------------

    @Nested
    inner class ChangePassword {

        @Test
        fun `unknown login throws UsernameNotFoundException`() {
            whenever(users.findByLoginForUpdate("ghost")).thenReturn(null)

            assertThrows<UsernameNotFoundException> { service.changePassword("ghost", "newPass1234") }
        }

        @Test
        fun `replaces password and clears the OTP plain text and flag`() {
            val target = user(
                login = "alice",
                encryptedPassword = bcrypt.encode("old"),
                properties = UserProperties(initialPassword = "raw-otp", passwordChangeRequired = true),
            )
            whenever(users.findByLoginForUpdate("alice")).thenReturn(target)
            whenever(users.save(any<User>())).thenAnswer { it.arguments[0] as User }

            service.changePassword("alice", "newSecret1234")

            val saved = argumentCaptor<User>()
            verify(users).save(saved.capture())
            assertTrue(bcrypt.matches("newSecret1234", saved.firstValue.encryptedPassword))
            assertNull(saved.firstValue.properties?.initialPassword)
            assertEquals(false, saved.firstValue.properties?.passwordChangeRequired)
        }

        @Test
        fun `tolerates a user that never had a properties object`() {
            val target = user(login = "alice", properties = null)
            whenever(users.findByLoginForUpdate("alice")).thenReturn(target)
            whenever(users.save(any<User>())).thenAnswer { it.arguments[0] as User }

            service.changePassword("alice", "newSecret1234") // must not throw

            val saved = argumentCaptor<User>()
            verify(users).save(saved.capture())
            assertNotNull(saved.firstValue.encryptedPassword)
        }
    }
}
