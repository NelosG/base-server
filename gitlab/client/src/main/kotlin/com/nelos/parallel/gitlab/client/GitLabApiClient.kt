package com.nelos.parallel.gitlab.client

import com.nelos.parallel.gitlab.client.vo.GitLabBranchInfo
import com.nelos.parallel.gitlab.client.vo.GitLabProjectInfo
import com.nelos.parallel.gitlab.client.vo.GitLabUserInfo

/**
 * Client for interacting with the GitLab REST API v4.
 * All project references use path (e.g. "root/lab1-openmp") instead of numeric IDs.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface GitLabApiClient {

    // --- Fork Management ---

    fun forkProject(projectPath: String, targetNamespace: String): GitLabProjectInfo

    /** All forks of the given project (paginated, full list). */
    fun getProjectForks(projectPath: String): List<GitLabProjectInfo>

    // --- Project & Branch Listing ---

    /**
     * Up to 100 projects. With `search` - used as typeahead (first 100 matches
     * are plenty for an autocomplete dropdown). Without `search` - admin views
     * needing more should narrow with a search term.
     */
    fun listProjects(search: String? = null): List<GitLabProjectInfo>

    /** Up to 100 branches of the project (typical repo has <20). */
    fun listBranches(projectPath: String): List<GitLabBranchInfo>

    // --- User Lookup ---

    /** All GitLab users (paginated, full list - used for fork-status matching). */
    fun listUsers(): List<GitLabUserInfo>
}
