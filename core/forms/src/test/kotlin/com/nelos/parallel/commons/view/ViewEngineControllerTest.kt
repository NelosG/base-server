package com.nelos.parallel.commons.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import com.nelos.parallel.commons.view.vo.ViewRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the reflection-based RPC dispatch used by the front-end:
 * authentication/authorisation gates, argument arity, type coercion, and the
 * "unknown service" guard that protects Spring's bean-name space from probing.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class ViewEngineControllerTest {

    // --- beans used as discovery targets --------------------------------

    @ViewService("publicSvc", public = true)
    open class PublicSvc {
        fun echo(value: String): String = "got: $value"
        fun greet(name: String, age: Int): String = "$name/$age"
        fun nothing() {}
        fun usingRequest(req: HttpServletRequest): String = req.requestURI
    }

    @ViewService("privateSvc")
    open class PrivateSvc {
        fun secret(): String = "shhh"
    }

    @ViewService("adminSvc", roles = [AppRole.ADMIN])
    open class AdminSvc {
        fun adminOnly(): String = "admin-data"
    }

    @ViewService("throwingSvc", public = true)
    open class ThrowingSvc {
        fun boom(): String = error("intentional failure")
    }

    // --- plumbing -------------------------------------------------------

    private val publicSvc = PublicSvc()
    private val privateSvc = PrivateSvc()
    private val adminSvc = AdminSvc()
    private val throwingSvc = ThrowingSvc()

    private val applicationContext: ApplicationContext = mock {
        on { getBeansWithAnnotation(ViewService::class.java) } doReturn mapOf(
            PUBLIC_BEAN to publicSvc,
            PRIVATE_BEAN to privateSvc,
            ADMIN_BEAN to adminSvc,
            THROWING_BEAN to throwingSvc,
        )
        on { getBean(PUBLIC_BEAN) } doReturn publicSvc
        on { getBean(PRIVATE_BEAN) } doReturn privateSvc
        on { getBean(ADMIN_BEAN) } doReturn adminSvc
        on { getBean(THROWING_BEAN) } doReturn throwingSvc
    }

    private val controller = ViewEngineController(applicationContext, ObjectMapper())

    private val httpRequest: HttpServletRequest = mock {
        on { requestURI } doReturn REQUEST_URI
    }
    private val httpResponse: HttpServletResponse = mock()

    @BeforeEach
    fun anonymous() = SecurityContextHolder.clearContext()

    @AfterEach
    fun cleanContext() = SecurityContextHolder.clearContext()

    private fun authenticated(vararg roles: String) {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken("alice", null, *roles)
    }

    // --- public path ----------------------------------------------------

    @Nested
    inner class PublicAccess {

        @Test
        fun `invokes a public service method and returns its result wrapped in ok`() {
            val req = ViewRequest(service = PUBLIC_BEAN, method = "echo", args = listOf("hello"))

            val resp = controller.invoke(req, httpRequest, httpResponse)

            assertEquals(HttpStatus.OK, resp.statusCode)
            assertEquals(true, resp.body?.success)
            assertEquals("got: hello", resp.body?.data)
        }

        @Test
        fun `void return becomes ok with null data`() {
            val req = ViewRequest(service = PUBLIC_BEAN, method = "nothing", args = emptyList())

            val resp = controller.invoke(req, httpRequest, httpResponse)

            assertEquals(true, resp.body?.success)
            assertNull(resp.body?.data)
        }

        @Test
        fun `Jackson coerces JSON-style arguments into the parameter types`() {
            // args come over the wire as JSON; Jackson converts each value to the method's parameter type.
            val req = ViewRequest(service = PUBLIC_BEAN, method = "greet", args = listOf("bob", "42"))

            val resp = controller.invoke(req, httpRequest, httpResponse)

            assertEquals("bob/42", resp.body?.data)
        }

        @Test
        fun `HttpServletRequest parameters are injected and do not consume args`() {
            val req = ViewRequest(service = PUBLIC_BEAN, method = "usingRequest", args = emptyList())

            val resp = controller.invoke(req, httpRequest, httpResponse)

            assertEquals(REQUEST_URI, resp.body?.data)
        }
    }

    // --- unknown service guard ------------------------------------------

    @Test
    fun `unknown service name returns 400 with a generic message (must NOT leak Spring bean-resolution details)`() {
        val req = ViewRequest(service = "nope", method = "x")

        val resp = controller.invoke(req, httpRequest, httpResponse)

        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        assertEquals("Unknown view service", resp.body?.error)
    }

    // --- auth / role gates ----------------------------------------------

    @Nested
    inner class AuthGates {

        @Test
        fun `private service called anonymously returns 401`() {
            val req = ViewRequest(service = PRIVATE_BEAN, method = "secret")

            val resp = controller.invoke(req, httpRequest, httpResponse)

            assertEquals(HttpStatus.UNAUTHORIZED, resp.statusCode)
        }

        @Test
        fun `private service called authenticated proceeds`() {
            authenticated(AppRole.ROLE_USER)
            val req = ViewRequest(service = PRIVATE_BEAN, method = "secret")

            val resp = controller.invoke(req, httpRequest, httpResponse)

            assertEquals(HttpStatus.OK, resp.statusCode)
            assertEquals("shhh", resp.body?.data)
        }

        @Test
        fun `role-restricted service rejects users without the required role`() {
            authenticated(AppRole.ROLE_USER) // not ADMIN
            val req = ViewRequest(service = ADMIN_BEAN, method = "adminOnly")

            val resp = controller.invoke(req, httpRequest, httpResponse)

            assertEquals(HttpStatus.FORBIDDEN, resp.statusCode)
        }

        @Test
        fun `role-restricted service accepts a user with the matching role`() {
            authenticated(AppRole.ROLE_ADMIN)
            val req = ViewRequest(service = ADMIN_BEAN, method = "adminOnly")

            val resp = controller.invoke(req, httpRequest, httpResponse)

            assertEquals(HttpStatus.OK, resp.statusCode)
            assertEquals("admin-data", resp.body?.data)
        }
    }

    // --- method resolution + error handling -----------------------------

    @Test
    fun `method that does not exist returns 400 with a clear message`() {
        val req = ViewRequest(service = PUBLIC_BEAN, method = "doesNotExist", args = emptyList())

        val resp = controller.invoke(req, httpRequest, httpResponse)

        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        assertNotNull(resp.body?.error)
        assertTrue(resp.body?.error?.contains("doesNotExist") == true)
    }

    @Test
    fun `wrong argument count returns 400 instead of attempting reflection`() {
        // `echo` takes one arg; we pass two.
        val req = ViewRequest(service = PUBLIC_BEAN, method = "echo", args = listOf("a", "b"))

        val resp = controller.invoke(req, httpRequest, httpResponse)

        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `business exception inside the target method surfaces as 500 with the cause message`() {
        val req = ViewRequest(service = THROWING_BEAN, method = "boom", args = emptyList())

        val resp = controller.invoke(req, httpRequest, httpResponse)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.statusCode)
        assertTrue(resp.body?.error?.contains("intentional failure") == true)
    }

    companion object {
        private const val PUBLIC_BEAN = "publicSvc"
        private const val PRIVATE_BEAN = "privateSvc"
        private const val ADMIN_BEAN = "adminSvc"
        private const val THROWING_BEAN = "throwingSvc"
        private const val REQUEST_URI = "/api/view/invoke"
    }
}
