package com.nelos.parallel.commons.security

/**
 * Application-wide role constants.
 *
 * Use bare names ([USER], [ADMIN], [API_CLIENT]) for `@ViewService(roles = ...)` annotations
 * and other contexts without the Spring Security prefix.
 *
 * Use prefixed variants ([ROLE_USER], [ROLE_ADMIN], [ROLE_API_CLIENT]) for Spring Security
 * authorities (granted authorities, `hasAuthority()`, `SecurityContext`, etc.).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
object AppRole {

    const val PREFIX = "ROLE_"

    const val USER = "USER"
    const val ADMIN = "ADMIN"
    const val API_CLIENT = "API_CLIENT"

    const val ROLE_USER = PREFIX + USER
    const val ROLE_ADMIN = PREFIX + ADMIN
    const val ROLE_API_CLIENT = PREFIX + API_CLIENT
}
