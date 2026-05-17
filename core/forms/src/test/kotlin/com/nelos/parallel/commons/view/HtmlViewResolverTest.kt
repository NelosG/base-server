package com.nelos.parallel.commons.view

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.Ordered
import org.springframework.core.io.ByteArrayResource
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class HtmlViewResolverTest {

    private val resolver = HtmlViewResolver()

    @Test
    fun `resolver runs at the lowest precedence so framework defaults win`() {
        // It's a catch-all fallback - anything more specific (Thymeleaf etc.) must
        // match first.
        assertEquals(Ordered.LOWEST_PRECEDENCE, resolver.order)
    }

    @Test
    fun `unknown template name returns null so other resolvers get a chance`() {
        val view = resolver.resolveViewName("definitely-missing-template-${System.nanoTime()}", Locale.US)

        assertNull(view)
    }

    @Test
    fun `HtmlView pipes the resource to the response output stream and sets the content-type`() {
        val payload = HTML_PAYLOAD.toByteArray()
        val view = HtmlView(ByteArrayResource(payload))

        val request: HttpServletRequest = mock()
        val response: HttpServletResponse = mock()
        val sink = ByteArrayOutputStream()
        val servletStream = object : ServletOutputStream() {
            override fun write(b: Int) {
                sink.write(b)
            }

            override fun isReady() = true
            override fun setWriteListener(listener: WriteListener?) {}
        }
        whenever(response.outputStream).thenReturn(servletStream)

        view.render(emptyMap<String, Any>(), request, response)

        assertEquals(HTML_PAYLOAD, sink.toString())
        // Spring's AbstractView sets the content-type header on the response.
        verify(response).contentType = HTML_CONTENT_TYPE
    }

    companion object {
        private const val HTML_PAYLOAD = "<html><body>Hello</body></html>"
        private const val HTML_CONTENT_TYPE = "text/html;charset=UTF-8"
    }
}
