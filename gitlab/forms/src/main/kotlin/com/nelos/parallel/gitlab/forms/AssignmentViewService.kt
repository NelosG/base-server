package com.nelos.parallel.gitlab.forms

import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import com.nelos.parallel.gitlab.client.GitLabApiClient
import com.nelos.parallel.gitlab.forms.vo.*
import com.nelos.parallel.gitlab.service.GitlabUserService
import com.nelos.parallel.pipeline.data.entity.Assignment
import com.nelos.parallel.pipeline.data.service.AssignmentService
import com.nelos.parallel.pipeline.data.service.StudentGroupMemberService
import com.nelos.parallel.pipeline.data.service.StudentGroupService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.assignmentViewService", roles = [AppRole.ADMIN])
class AssignmentViewService(
    private val assignmentService: AssignmentService,
    private val gitLabApiClient: GitLabApiClient,
    private val studentGroupService: StudentGroupService,
    private val studentGroupMemberService: StudentGroupMemberService,
    private val userService: UserService,
    private val gitlabUserService: GitlabUserService,
    @param:Value("\${gitlab.url:}") private val gitlabUrl: String,
) {

    // --- Assignment CRUD ---

    fun getAssignments(): List<AssignmentView> {
        return assignmentService.findAll().map { it.toView() }
    }

    fun getAssignment(id: Long): AssignmentView {
        return assignmentService.findById(id).toView()
    }

    fun saveAssignment(data: SaveAssignmentRequest): AssignmentView {
        val assignment = if (data.id != null) {
            assignmentService.findById(data.id)
        } else {
            Assignment()
        }

        assignment.code = data.code
        assignment.name = data.name
        assignment.description = data.description
        assignment.gitlabProjectPath = data.gitlabProjectPath
        assignment.testRepoUrl = data.testRepoUrl
        assignment.testRepoBranch = data.testRepoBranch
        assignment.memoryLimitMb = data.memoryLimitMb
        assignment.threads = data.threads
        assignment.wallTimeSec = data.wallTimeSec
        assignment.cpuTimeSec = data.cpuTimeSec
        assignment.maxProcesses = data.maxProcesses
        assignment.warmupIterations = data.warmupIterations
        assignment.active = data.active
        assignment.evaluatorScript = data.evaluatorScript

        return assignmentService.save(assignment).toView()
    }

    /**
     * Toggles only the active flag without re-sending the full payload. The
     * list-page Activate/Deactivate button uses this so flipping one boolean
     * doesn't accidentally null out resource-limit fields on the way through
     * the PUT-style `saveAssignment`.
     */
    fun setAssignmentActive(id: Long, active: Boolean): AssignmentView {
        val assignment = assignmentService.findById(id)
        assignment.active = active
        return assignmentService.save(assignment).toView()
    }

    fun deleteAssignment(id: Long) {
        assignmentService.remove(id)
    }

    // --- GitLab Project & Branch Selection ---

    fun getGitLabProjects(search: String?): List<GitLabProjectView> {
        // GitLab's ?search= filter matches the project NAME only - so "root"
        // (a namespace) or "root/lab1-openmp" (full path) both return zero
        // hits server-side. We fetch all candidates and filter against
        // pathWithNamespace + name ourselves so typing any visible substring
        // works the way users expect.
        val q = search?.trim()?.takeIf { it.isNotBlank() }?.lowercase()
        return gitLabApiClient.listProjects(null)
            .filter { it.forkedFromProject == null && it.markedForDeletionAt == null }
            .filter {
                q == null ||
                    it.pathWithNamespace?.lowercase()?.contains(q) == true ||
                    it.name?.lowercase()?.contains(q) == true
            }
            .map {
                GitLabProjectView(
                    name = it.name,
                    pathWithNamespace = it.pathWithNamespace,
                    gitHttpUrl = it.gitHttpUrl,
                )
            }
    }

    fun getProjectBranches(projectPath: String): List<GitLabBranchView> {
        return gitLabApiClient.listBranches(projectPath).map {
            GitLabBranchView(name = it.name, default_ = it.default_)
        }
    }

    // --- Fork Management ---

    fun getExistingForks(assignmentId: Long): List<ForkView> {
        val assignment = assignmentService.findById(assignmentId)
        val projectPath = assignment.gitlabProjectPath ?: error("Assignment has no GitLab project path")
        return gitLabApiClient.getProjectForks(projectPath)
            .filter { it.markedForDeletionAt == null }
            .map { ForkView(pathWithNamespace = it.pathWithNamespace, webUrl = it.webUrl) }
    }

    fun createForks(data: CreateForksRequest): CreateForksResultView {
        val assignment = assignmentService.findById(data.assignmentId ?: error("assignmentId is required"))
        val projectPath = assignment.gitlabProjectPath ?: error("Assignment has no GitLab project path")
        val groupIds = data.groupIds ?: listOfNotNull(data.groupId)
        LOG.info("Creating forks for assignment '{}' (project={}), groups={}", assignment.code, projectPath, groupIds)
        val usernames = if (groupIds.isNotEmpty()) {
            val gitlabByUserId = gitlabUserService.findAll().associateBy { it.userId }
            groupIds.flatMap { gid ->
                val members = studentGroupMemberService.findByGroupId(gid)
                val names = members.mapNotNull { gitlabByUserId[it.userId]?.gitLabName }
                LOG.info("Group id={} has {} members, gitlab names: {}", gid, members.size, names)
                names
            }.distinct()
        } else {
            data.usernames ?: emptyList()
        }
        // Pre-load existing forks once so we can skip students who already have one
        // (re-running createForks for a partially-completed group used to surface
        // each existing fork as a 409 "already exists" error from GitLab).
        val existingForkByNs: Map<String, String?> = gitLabApiClient.getProjectForks(projectPath)
            .filter { it.markedForDeletionAt == null }
            .mapNotNull { f -> f.pathWithNamespace?.substringBefore("/")?.let { it to f.webUrl } }
            .toMap()
        val results = usernames.map { username ->
            if (username in existingForkByNs) {
                LOG.info("Fork already exists for '{}' - skipping", username)
                return@map ForkResultEntry(
                    username = username,
                    success = true,
                    forkUrl = existingForkByNs[username],
                )
            }
            try {
                LOG.info("Forking project {} to namespace '{}'", projectPath, username)
                val fork = gitLabApiClient.forkProject(projectPath, username)
                LOG.info("Fork created: {}", fork.pathWithNamespace)
                ForkResultEntry(username = username, success = true, forkUrl = fork.webUrl)
            } catch (e: Exception) {
                LOG.warn("Failed to fork for '{}': {}", username, e.message)
                ForkResultEntry(username = username, success = false, error = e.message)
            }
        }
        return CreateForksResultView(results = results)
    }

    fun getGroupsWithForkStatus(assignmentId: Long): List<GroupForkStatusView> {
        val assignment = assignmentService.findById(assignmentId)
        val projectPath = assignment.gitlabProjectPath ?: error("Assignment has no GitLab project path")
        val existingForks = gitLabApiClient.getProjectForks(projectPath)
            .filter { it.markedForDeletionAt == null }
            .mapNotNull { it.pathWithNamespace?.substringBefore("/") }
            .toSet()

        val gitlabUsers = gitLabApiClient.listUsers()
            .mapNotNull { it.username }
            .toSet()

        val users = userService.findAll().associateBy { it.id }
        val gitlabByUserId = gitlabUserService.findAll().associateBy { it.userId }
        return studentGroupService.findAll().mapNotNull { group ->
            val groupId = group.id ?: return@mapNotNull null
            val members = studentGroupMemberService.findByGroupId(groupId)
            val memberStatuses = members.map { m ->
                val gitlabName = gitlabByUserId[m.userId]?.gitLabName ?: ""
                MemberForkStatus(
                    username = gitlabName,
                    displayName = users[m.userId]?.displayName,
                    hasFork = gitlabName in existingForks,
                    gitlabExists = gitlabName in gitlabUsers,
                )
            }
            val missingCount = memberStatuses.count { it.hasFork != true && it.gitlabExists == true }
            val unavailableCount = memberStatuses.count { it.gitlabExists != true }
            GroupForkStatusView(
                id = group.id,
                name = group.name,
                memberCount = members.size,
                missingForkCount = missingCount,
                unavailableCount = unavailableCount,
                members = memberStatuses,
            )
        }
    }

    private fun Assignment.toView() = AssignmentView(
        id = id,
        code = code,
        name = name,
        description = description,
        gitlabProjectPath = gitlabProjectPath,
        testRepoUrl = testRepoUrl,
        testRepoBranch = testRepoBranch,
        memoryLimitMb = memoryLimitMb,
        threads = threads,
        wallTimeSec = wallTimeSec,
        cpuTimeSec = cpuTimeSec,
        maxProcesses = maxProcesses,
        warmupIterations = warmupIterations,
        active = active,
        evaluatorScript = evaluatorScript,
    )

    companion object {
        private val LOG = LoggerFactory.getLogger(AssignmentViewService::class.java)
    }
}
