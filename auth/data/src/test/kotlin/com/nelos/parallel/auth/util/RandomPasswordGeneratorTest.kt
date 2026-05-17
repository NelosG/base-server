package com.nelos.parallel.auth.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class RandomPasswordGeneratorTest {

    @Test
    fun `default length is 16 - guards against accidentally weakening OTPs`() {
        val pwd = RandomPasswordGenerator.generate()

        assertEquals(16, pwd.length)
    }

    @Test
    fun `only emits characters from the alphanumeric alphabet`() {
        val allowed = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        // length 256 also implicitly verifies that the length parameter is honoured.
        val pwd = RandomPasswordGenerator.generate(length = 256)

        assertEquals(256, pwd.length)
        assertTrue(pwd.all { it in allowed }, "Unexpected char in '$pwd'")
    }

    @Test
    fun `zero length is rejected`() {
        assertThrows<IllegalArgumentException> { RandomPasswordGenerator.generate(length = 0) }
    }

    @Test
    fun `negative length is rejected`() {
        assertThrows<IllegalArgumentException> { RandomPasswordGenerator.generate(length = -1) }
    }

    @Test
    fun `successive calls produce different passwords`() {
        // Collision probability for 16 chars x 62 alphabet over 1000 calls is ~10^-22 -
        // a real-world test failure here means the RNG is wired wrong, not bad luck.
        val passwords = List(1000) { RandomPasswordGenerator.generate() }.toSet()

        assertEquals(1000, passwords.size, "Generated passwords contain duplicates")
    }
}
