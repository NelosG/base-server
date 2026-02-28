package com.nelos.parallel.commons.view.service

import org.springframework.core.annotation.AliasFor
import org.springframework.stereotype.Service

/**
 * Marks a class as a view service bean invocable through the ViewEngine.
 *
 * @property value the Spring bean name (aliased to [org.springframework.stereotype.Service.value])
 * @property public if `true`, the service is accessible without authentication
 * @property roles if non-empty, only users with at least one of the specified roles may invoke this service
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Service
annotation class ViewService(
    @get:AliasFor(annotation = Service::class)
    val value: String = "",
    val public: Boolean = false,
    val roles: Array<String> = [],
)