package com.nelos.parallel.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTCreationException
import com.nelos.parallel.auth.vo.UserData
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.jwtTokenProvider")
class JwtTokenProvider {

    @Value("\${security.jwt.token.secret-key}")
    private val jwtSecret: String? = null

    fun generateAccessToken(user: UserData): String {
        try {
            val algorithm = Algorithm.HMAC256(jwtSecret)
            val login = user.login
            return JWT.create()
                .withSubject(login)
                .withClaim("login", login)
                .withExpiresAt(genAccessExpirationDate())
                .sign(algorithm)
        } catch (exception: JWTCreationException) {
            throw JWTCreationException("Error while generating token", exception)
        }
    }

    fun validateToken(token: String): String {
        val algorithm = Algorithm.HMAC256(jwtSecret)
        return JWT.require(algorithm)
            .build()
            .verify(token)
            .subject

    }

    companion object {
        const val EXPIRATION_IN_DAYS = 30
        fun genAccessExpirationDate(): Instant {
            return LocalDateTime.now().plusHours(EXPIRATION_IN_DAYS.toLong()).toInstant(ZoneOffset.UTC)
        }
    }
}