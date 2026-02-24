package com.nelos.parallel.commons.view

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver
import org.springframework.web.servlet.view.AbstractView
import java.util.*

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Configuration
class HtmlViewResolverConfig {

    @Bean
    fun htmlViewResolver() = HtmlViewResolver()
}

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class HtmlViewResolver : ViewResolver, Ordered {

    override fun getOrder() = Ordered.LOWEST_PRECEDENCE

    override fun resolveViewName(viewName: String, locale: Locale): View? {
        val resource = ClassPathResource("templates/$viewName.html")
        return if (resource.exists()) HtmlView(resource) else null
    }
}

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class HtmlView(private val resource: Resource) : AbstractView() {

    init {
        contentType = "text/html;charset=UTF-8"
    }

    override fun renderMergedOutputModel(
        model: MutableMap<String, Any>,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        response.contentType = contentType
        resource.inputStream.use { it.copyTo(response.outputStream) }
    }
}
