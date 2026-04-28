package com.nelos.parallel.auth.util

import java.security.SecureRandom

/**
 * Generates random one-time passwords for newly created or password-reset users.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
object RandomPasswordGenerator {

    private const val DEFAULT_LENGTH = 16
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    fun generate(length: Int = DEFAULT_LENGTH): String {
        require(length > 0) { "length must be positive" }
        val chars = CharArray(length)
        for (i in 0 until length) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)]
        }
        return String(chars)
    }
}
