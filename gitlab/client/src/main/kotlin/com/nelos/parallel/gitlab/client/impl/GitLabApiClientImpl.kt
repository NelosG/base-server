package com.nelos.parallel.gitlab.client.impl

import com.nelos.parallel.gitlab.client.GitLabApiClient
import com.nelos.parallel.gitlab.client.impl.GitLabApiClientImpl.Companion.MAX_TOTAL_ROWS
import com.nelos.parallel.gitlab.client.vo.GitLabBranchInfo
import com.nelos.parallel.gitlab.client.vo.GitLabProjectInfo
import com.nelos.parallel.gitlab.client.vo.GitLabUserInfo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Service("prl.gitLabApiClient")
class GitLabApiClientImpl internal constructor(
    private val restClient: RestClient,
) : GitLabApiClient {

    constructor(
        @Value("\${gitlab.url}") gitlabUrl: String,
        @Value("\${gitlab.api.token}") apiToken: String,
    ) : this(defaultRestClient(gitlabUrl, apiToken))

    override fun forkProject(projectPath: String, targetNamespace: String): GitLabProjectInfo {
        return restClient.post()
            .uri(projectUri(projectPath, "/fork"))
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("namespace_path" to targetNamespace, "visibility" to "private"))
            .retrieve()
            .body(GitLabProjectInfo::class.java)
            ?: error("Empty response when forking project $projectPath to $targetNamespace")
    }

    override fun getProjectForks(projectPath: String): List<GitLabProjectInfo> =
        fetchAll(LIST_OF_PROJECTS) { page ->
            projectUri(projectPath, "/forks?per_page=$PER_PAGE&page=$page")
        }

    override fun listProjects(search: String?): List<GitLabProjectInfo> =
        fetchAll(LIST_OF_PROJECTS) { page ->
            val params = StringBuilder()
                .append("per_page=$PER_PAGE&order_by=name&sort=asc&page=").append(page)
            if (!search.isNullOrBlank()) params.append("&search=").append(encode(search))
            URI.create("/api/v4/projects?$params")
        }

    override fun listBranches(projectPath: String): List<GitLabBranchInfo> =
        fetchAll(LIST_OF_BRANCHES) { page ->
            projectUri(projectPath, "/repository/branches?per_page=$PER_PAGE&page=$page")
        }

    override fun listUsers(): List<GitLabUserInfo> =
        fetchAll(LIST_OF_USERS) { page ->
            URI.create("/api/v4/users?per_page=$PER_PAGE&without_project_bots=true&page=$page")
        }

    // --- Internal ---

    /**
     * Walks all pages via `X-Next-Page` until empty. Single helper for every
     * list endpoint - keeps results complete (no silent first-page truncation)
     * and the loop logic in one place.
     *
     * Safety: [MAX_TOTAL_ROWS] caps the total rows fetched so a buggy GitLab
     * response (non-empty next page indefinitely, header parser glitch) can't
     * make us loop forever; the cap is logged when triggered.
     */
    private fun <T> fetchAll(
        responseType: ParameterizedTypeReference<List<T>>,
        uri: (page: Int) -> URI,
    ): List<T> {
        val all = mutableListOf<T>()
        var page = 1
        while (true) {
            val resp = restClient.get().uri(uri(page)).retrieve().toEntity(responseType)
            val body = resp.body
            if (body.isNullOrEmpty()) return all
            all.addAll(body)
            if (all.size >= MAX_TOTAL_ROWS) {
                LOG.warn(
                    "GitLab pagination capped at {} rows for {}; remaining pages skipped",
                    MAX_TOTAL_ROWS, uri(1)
                )
                return all
            }
            val next = resp.headers.getFirst(HEADER_NEXT_PAGE)?.trim().orEmpty()
            if (next.isEmpty()) return all
            page = next.toIntOrNull()?.takeIf { it > page } ?: return all
        }
    }

    companion object {
        private const val PRIVATE_TOKEN_HEADER = "PRIVATE-TOKEN"
        private const val PER_PAGE = 100
        private const val MAX_TOTAL_ROWS = 5_000
        private const val HEADER_NEXT_PAGE = "X-Next-Page"
        private val LOG = LoggerFactory.getLogger(GitLabApiClientImpl::class.java)
        private val LIST_OF_PROJECTS = object : ParameterizedTypeReference<List<GitLabProjectInfo>>() {}
        private val LIST_OF_BRANCHES = object : ParameterizedTypeReference<List<GitLabBranchInfo>>() {}
        private val LIST_OF_USERS = object : ParameterizedTypeReference<List<GitLabUserInfo>>() {}

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8)

        private fun projectUri(projectPath: String, suffix: String = ""): URI =
            URI.create("/api/v4/projects/${encode(projectPath)}$suffix")

        // Explicit timeouts - JDK defaults are unlimited, a hung GitLab call
        // would block the Tomcat worker forever.
        private fun defaultRestClient(gitlabUrl: String, apiToken: String): RestClient =
            RestClient.builder()
                .baseUrl(gitlabUrl)
                .defaultHeader(PRIVATE_TOKEN_HEADER, apiToken)
                .requestFactory(SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(5))
                    setReadTimeout(Duration.ofSeconds(30))
                })
                .build()
    }
}
