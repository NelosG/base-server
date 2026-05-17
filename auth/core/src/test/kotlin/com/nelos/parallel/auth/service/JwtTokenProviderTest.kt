package com.nelos.parallel.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.vo.UserData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertEquals

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class JwtTokenProviderTest {

    private val secret = "test-secret-with-enough-entropy-for-HMAC256"
    private val provider = JwtTokenProvider(secret)

    private fun userData(login: String = "alice") =
        UserData(id = 1L, login = login, encryptedPassword = "x", type = UserType.STUDENT)

    @Test
    fun `generate and validate returns the same login`() {
        val token = provider.generateAccessToken(userData(login = "bob"))

        val subject = provider.validateToken(token)

        assertEquals("bob", subject)
    }

    @Test
    fun `validation rejects tokens signed with a different secret`() {
        val foreign = JWT.create()
            .withIssuer("parallel-server")
            .withSubject("alice")
            .withClaim("login", "alice")
            .withExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
            .sign(Algorithm.HMAC256("a-completely-different-secret"))

        assertThrows<JWTVerificationException> { provider.validateToken(foreign) }
    }

    @Test
    fun `validation rejects tokens with a different issuer`() {
        val wrongIssuer = JWT.create()
            .withIssuer("not-parallel-server")
            .withSubject("alice")
            .withClaim("login", "alice")
            .withExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
            .sign(Algorithm.HMAC256(secret))

        assertThrows<JWTVerificationException> { provider.validateToken(wrongIssuer) }
    }

    @Test
    fun `validation rejects expired tokens`() {
        val expired = JWT.create()
            .withIssuer("parallel-server")
            .withSubject("alice")
            .withClaim("login", "alice")
            .withExpiresAt(Date.from(Instant.now().minusSeconds(60)))
            .sign(Algorithm.HMAC256(secret))

        assertThrows<JWTVerificationException> { provider.validateToken(expired) }
    }

    @Test
    fun `validation rejects malformed tokens`() {
        assertThrows<JWTVerificationException> { provider.validateToken("garbage.not-a-jwt") }
    }

    @Test
    fun `issued token carries an expiration roughly seven days in the future`() {
        val token = provider.generateAccessToken(userData())

        val expiresAt = JWT.decode(token).expiresAt.toInstant()
        val expectedMin = Instant.now().plus(6, ChronoUnit.DAYS)
        val expectedMax = Instant.now().plus(8, ChronoUnit.DAYS)

        assert(expiresAt.isAfter(expectedMin) && expiresAt.isBefore(expectedMax)) {
            "expiresAt=$expiresAt outside [$expectedMin, $expectedMax]"
        }
    }
}
