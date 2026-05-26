package com.nelos.parallel.gitlab.client.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.nelos.parallel.gitlab.client.vo.GitLabProjectInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hits an in-memory MockRestServiceServer to drive [GitLabApiClientImpl]'s
 * URL composition, header wiring, body shape, and pagination loop. No real
 * GitLab instance is touched - the server matches the request and replies
 * with the canned JSON.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class GitLabApiClientImplTest {

    private val mapper = ObjectMapper()
    private lateinit var server: MockRestServiceServer
    private lateinit var client: GitLabApiClientImpl

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
            .baseUrl("http://gitlab.local")
            .defaultHeader("PRIVATE-TOKEN", "secret-token")
        server = MockRestServiceServer.bindTo(builder).build()
        client = GitLabApiClientImpl(builder.build())
    }

    private fun json(payload: Any) = withSuccess(mapper.writeValueAsString(payload), MediaType.APPLICATION_JSON)

    @Nested
    inner class ForkProject {

        @Test
        fun `posts to api v4 projects path fork with namespace_path body and PRIVATE-TOKEN header`() {
            server.expect(requestTo("http://gitlab.local/api/v4/projects/root%2Flab1-openmp/fork"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PRIVATE-TOKEN", "secret-token"))
                .andExpect(content().string("""{"namespace_path":"student","visibility":"private"}"""))
                .andRespond(
                    json(
                        GitLabProjectInfo(
                            name = "lab1-openmp",
                            pathWithNamespace = "student/lab1-openmp",
                        )
                    )
                )

            val result = client.forkProject("root/lab1-openmp", "student")

            assertEquals("lab1-openmp", result.name)
            assertEquals("student/lab1-openmp", result.pathWithNamespace)
        }

        @Test
        fun `URL-encodes special chars in the project path`() {
            // Group paths can contain slashes; the / itself must encode as %2F
            // since it would otherwise terminate the projects path segment.
            server.expect(requestTo("http://gitlab.local/api/v4/projects/a%2Fb%2Fc/fork"))
                .andRespond(json(GitLabProjectInfo(name = "c")))

            client.forkProject("a/b/c", "ns")
        }
    }

    @Nested
    inner class Pagination {

        @Test
        fun `walks every page driven by X-Next-Page header`() {
            // Returns 2-row pages until X-Next-Page is empty.
            val headers1 = HttpHeaders().apply { add("X-Next-Page", "2") }
            val headers2 = HttpHeaders().apply { add("X-Next-Page", "3") }
            val headers3 = HttpHeaders()
            server.expect(requestTo("http://gitlab.local/api/v4/projects/root%2Flab/forks?per_page=100&page=1"))
                .andRespond(
                    withSuccess(
                        mapper.writeValueAsString(
                            listOf(GitLabProjectInfo(name = "p1"), GitLabProjectInfo(name = "p2"))
                        ),
                        MediaType.APPLICATION_JSON,
                    ).headers(headers1)
                )
            server.expect(requestTo("http://gitlab.local/api/v4/projects/root%2Flab/forks?per_page=100&page=2"))
                .andRespond(
                    withSuccess(
                        mapper.writeValueAsString(
                            listOf(GitLabProjectInfo(name = "p3"), GitLabProjectInfo(name = "p4"))
                        ),
                        MediaType.APPLICATION_JSON,
                    ).headers(headers2)
                )
            server.expect(requestTo("http://gitlab.local/api/v4/projects/root%2Flab/forks?per_page=100&page=3"))
                .andRespond(
                    withSuccess(
                        mapper.writeValueAsString(listOf(GitLabProjectInfo(name = "p5"))),
                        MediaType.APPLICATION_JSON,
                    ).headers(headers3)
                )

            val all = client.getProjectForks("root/lab")

            assertEquals(listOf("p1", "p2", "p3", "p4", "p5"), all.mapNotNull { it.name })
        }

        @Test
        fun `empty first page returns an empty list without following X-Next-Page`() {
            val headers = HttpHeaders().apply { add("X-Next-Page", "2") }
            server.expect(ExpectedCount.once(),
                requestTo("http://gitlab.local/api/v4/projects/root%2Flab/forks?per_page=100&page=1"))
                .andRespond(
                    withSuccess(mapper.writeValueAsString(emptyList<GitLabProjectInfo>()), MediaType.APPLICATION_JSON)
                        .headers(headers)
                )

            val result = client.getProjectForks("root/lab")

            assertTrue(result.isEmpty())
            server.verify()
        }

        @Test
        fun `missing X-Next-Page header stops pagination after the first page`() {
            server.expect(ExpectedCount.once(),
                requestTo("http://gitlab.local/api/v4/projects/root%2Flab/forks?per_page=100&page=1"))
                .andRespond(json(listOf(GitLabProjectInfo(name = "only"))))

            val result = client.getProjectForks("root/lab")

            assertEquals(listOf("only"), result.mapNotNull { it.name })
            server.verify()
        }

        @Test
        fun `X-Next-Page that does not advance terminates the loop - protects against header glitch`() {
            // X-Next-Page=1 on the response to page=1 would loop forever otherwise.
            val headers = HttpHeaders().apply { add("X-Next-Page", "1") }
            server.expect(ExpectedCount.once(),
                requestTo("http://gitlab.local/api/v4/projects/root%2Flab/forks?per_page=100&page=1"))
                .andRespond(json(listOf(GitLabProjectInfo(name = "p1"))).headers(headers))

            val result = client.getProjectForks("root/lab")

            assertEquals(1, result.size)
            server.verify()
        }
    }

    @Nested
    inner class ListProjects {

        @Test
        fun `without search adds per_page order_by sort and page params`() {
            server.expect(requestTo("http://gitlab.local/api/v4/projects?per_page=100&order_by=name&sort=asc&page=1"))
                .andRespond(json(emptyList<GitLabProjectInfo>()))

            client.listProjects(search = null)
        }

        @Test
        fun `URL-encodes a search term with spaces and slashes`() {
            server.expect(requestTo(
                "http://gitlab.local/api/v4/projects?per_page=100&order_by=name&sort=asc&page=1&search=hello+world%2Fnested"
            )).andRespond(json(emptyList<GitLabProjectInfo>()))

            client.listProjects(search = "hello world/nested")
        }

        @Test
        fun `blank search is treated as no search`() {
            server.expect(requestTo("http://gitlab.local/api/v4/projects?per_page=100&order_by=name&sort=asc&page=1"))
                .andRespond(json(emptyList<GitLabProjectInfo>()))

            client.listProjects(search = "  ")
        }
    }

    @Nested
    inner class ListBranches {

        @Test
        fun `GETs api v4 projects path repository branches with per_page and page`() {
            server.expect(requestTo(
                "http://gitlab.local/api/v4/projects/root%2Flab/repository/branches?per_page=100&page=1"
            )).andRespond(json(emptyList<Any>()))

            client.listBranches("root/lab")
        }
    }

    @Nested
    inner class ListUsers {

        @Test
        fun `GETs api v4 users with without_project_bots filter`() {
            server.expect(requestTo(
                "http://gitlab.local/api/v4/users?per_page=100&without_project_bots=true&page=1"
            )).andRespond(json(emptyList<Any>()))

            client.listUsers()
        }
    }
}
