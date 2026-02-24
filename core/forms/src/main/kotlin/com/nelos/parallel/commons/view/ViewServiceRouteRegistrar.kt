package com.nelos.parallel.commons.view

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Automatically registers view controllers for all HTML templates found on the classpath.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Configuration
class PageRouteRegistrar : WebMvcConfigurer {

    override fun addViewControllers(registry: ViewControllerRegistry) {
        val resources = PathMatchingResourcePatternResolver()
            .getResources("classpath*:/templates/*.html")
        for (resource in resources) {
            val name = resource.filename?.removeSuffix(".html") ?: continue
            registry.addViewController("/$name").setViewName(name)
        }
        registry.addViewController("/").setViewName("index")
    }
}
