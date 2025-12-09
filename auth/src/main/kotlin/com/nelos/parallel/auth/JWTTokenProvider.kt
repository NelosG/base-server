package com.nelos.parallel.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTCreationException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.nelos.parallel.entity.User

import org.springframework.beans.factory.annotation.Value

import org.springframework.stereotype.Service

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.JWTTokenProvider")
class JWTTokenProvider {

    @Value("\${security.jwt.token.secret-key}")
    private val jwtSecret: String? = null

    fun generateAccessToken(user: User): String {
        try {
            val algorithm = Algorithm.HMAC256(jwtSecret)
            val login = user.login ?: error("No user name")
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
        try {
            val algorithm = Algorithm.HMAC256(jwtSecret)
            return JWT.require(algorithm)
                .build()
                .verify(token)
                .subject
        } catch (exception: JWTVerificationException) {
            throw JWTVerificationException("Error while validating token", exception)
        }
    }

    private fun genAccessExpirationDate(): Instant {
        return LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.of("-03:00"))
    }
}