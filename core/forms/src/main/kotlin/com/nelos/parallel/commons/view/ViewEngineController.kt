package com.nelos.parallel.commons.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.view.service.ViewService
import com.nelos.parallel.commons.view.vo.ViewRequest
import com.nelos.parallel.commons.view.vo.ViewResponse
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller that dispatches incoming [ViewRequest]s to [@ViewService][com.nelos.parallel.commons.view.service.ViewService]-annotated beans.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@RestController("prl.viewEngineController")
class ViewEngineController @Autowired constructor(
    private val applicationContext: ApplicationContext,
    private val objectMapper: ObjectMapper,
) {

    /**
     * Invokes the method specified in [request] on the target view service bean.
     */
    @PostMapping("/api/view/invoke")
    fun invoke(@RequestBody request: ViewRequest, response: HttpServletResponse): ResponseEntity<ViewResponse> {
        return try {
            val bean = applicationContext.getBean(request.service)

            val annotation = AnnotationUtils.findAnnotation(bean::class.java, ViewService::class.java)
                ?: return ResponseEntity.badRequest()
                    .body(ViewResponse.error("Bean '${request.service}' is not a @ViewService"))

            if (!annotation.public && !isAuthenticated()) {
                return ResponseEntity.status(401)
                    .body(ViewResponse.error("Unauthorized"))
            }

            val args = request.args ?: emptyList()

            val method = bean::class.java.methods.firstOrNull {
                it.name == request.method &&
                        it.parameterTypes.count { p -> p != HttpServletResponse::class.java } == args.size
            } ?: return ResponseEntity.badRequest()
                .body(ViewResponse.error("Method '${request.method}' with ${args.size} argument(s) not found"))

            val argIterator = args.iterator()
            val convertedArgs = method.parameterTypes.map { paramType ->
                if (paramType == HttpServletResponse::class.java) {
                    response
                } else {
                    argIterator.next()?.let { objectMapper.convertValue(it, paramType) }
                }
            }.toTypedArray()

            val result = method.invoke(bean, *convertedArgs)

            if (result == null || result == Unit) {
                ResponseEntity.ok(ViewResponse.ok())
            } else {
                ResponseEntity.ok(ViewResponse.ok(result))
            }
        } catch (e: Exception) {
            LOG.error("ViewEngine invocation error: ${request.service}.${request.method}", e)
            val cause = e.cause ?: e
            ResponseEntity.internalServerError()
                .body(ViewResponse.error(cause.message ?: "Unknown error"))
        }
    }

    private fun isAuthenticated(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication
        return auth != null && auth !is AnonymousAuthenticationToken && auth.isAuthenticated
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ViewEngineController::class.java)
    }
}
