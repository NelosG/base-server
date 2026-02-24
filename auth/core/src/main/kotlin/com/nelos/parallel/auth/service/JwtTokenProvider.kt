package com.nelos.parallel.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.nelos.parallel.auth.vo.UserData
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Provides JWT token generation and validation.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.jwtTokenProvider")
class JwtTokenProvider(
    @param:Value("\${security.jwt.token.secret-key}") private val jwtSecret: String,
) {

    /**
     * Generates a signed JWT access token for the given [user].
     *
     * @return the encoded JWT string
     */
    fun generateAccessToken(user: UserData): String {
        val algorithm = Algorithm.HMAC256(jwtSecret)
        return JWT.create()
            .withSubject(user.login)
            .withClaim("login", user.login)
            .withExpiresAt(genAccessExpirationDate())
            .sign(algorithm)
    }

    /**
     * Validates the given [token] and returns the subject (login) if valid.
     *
     * @throws [JWTVerificationException] if the token is invalid or expired
     */
    fun validateToken(token: String): String {
        val algorithm = Algorithm.HMAC256(jwtSecret)
        return JWT.require(algorithm)
            .build()
            .verify(token)
            .subject
    }

    companion object {
        const val EXPIRATION_IN_DAYS = 30

        private fun genAccessExpirationDate(): Instant {
            return LocalDateTime.now().plusDays(EXPIRATION_IN_DAYS.toLong()).toInstant(ZoneOffset.UTC)
        }
    }
}
