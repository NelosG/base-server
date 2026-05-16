package com.nelos.parallel.gitlab.forms

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import com.nelos.parallel.gitlab.entity.GitlabUser
import com.nelos.parallel.gitlab.forms.vo.CreateStudentRequest
import com.nelos.parallel.gitlab.forms.vo.StudentView
import com.nelos.parallel.gitlab.service.GitlabUserService
import com.nelos.parallel.pipeline.data.entity.StudentGroupMember
import com.nelos.parallel.pipeline.data.service.StudentGroupMemberService
import com.nelos.parallel.pipeline.data.service.StudentGroupService
import com.nelos.parallel.pipeline.data.service.SubmissionService
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional

/**
 * Admin-only management of students. Lists, creates, resets passwords, manages GitLab name
 * mapping, manages group membership, and exposes the data needed for the
 * "students without group" pseudo-filter.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.studentViewService", roles = [AppRole.ADMIN])
class StudentViewService(
    private val userService: UserService,
    private val userDetailsService: UserDetailsProviderService,
    private val gitlabUserService: GitlabUserService,
    private val groupService: StudentGroupService,
    private val groupMemberService: StudentGroupMemberService,
    private val submissionService: SubmissionService,
) {

    fun getStudents(groupId: Long?): List<StudentView> {
        val students = userService.findAll().filter { it.type == UserType.STUDENT }
        val gitlabByUserId = gitlabUserService.findAll().associateBy { it.userId }
        val members = groupMemberService.findAll()
        val groupsById = groupService.findAll().associateBy { it.id }
        val submissionCounts = submissionService.findAll().groupingBy { it.userId }.eachCount()

        val filtered = when (groupId) {
            null -> students  // all
            STUDENTS_WITHOUT_GROUP -> {
                val membered = members.mapNotNull { it.userId }.toSet()
                students.filter { it.id !in membered }
            }

            else -> {
                val ids = members.filter { it.groupId == groupId }.mapNotNull { it.userId }.toSet()
                students.filter { it.id in ids }
            }
        }

        return filtered.map { user ->
            user.toView(gitlabByUserId, members, groupsById, submissionCounts)
        }
    }

    fun getStudent(id: Long): StudentView {
        val user = userService.tryFindById(id) ?: error("Student $id not found")
        if (user.type != UserType.STUDENT) error("User $id is not a student")
        val gitlabByUserId = gitlabUserService.findAll().associateBy { it.userId }
        val members = groupMemberService.findAll()
        val groupsById = groupService.findAll().associateBy { it.id }
        val submissionCounts = submissionService.findAll().groupingBy { it.userId }.eachCount()
        return user.toView(gitlabByUserId, members, groupsById, submissionCounts)
    }

    @Transactional
    fun createStudent(data: CreateStudentRequest): StudentView {
        val login = data.login?.trim()?.takeIf { it.isNotBlank() }
            ?: error("login is required")
        val (user, _) = userDetailsService.createUserWithRandomPassword(
            login = login,
            displayName = data.displayName?.trim()?.takeIf { it.isNotBlank() },
            type = UserType.STUDENT,
        )
        val userId = user.id ?: error("user has no id after save")

        data.gitlabName?.trim()?.takeIf { it.isNotBlank() }?.let { gitlabName ->
            gitlabUserService.save(GitlabUser().apply {
                this.userId = userId
                this.gitLabName = gitlabName
            })
        }
        data.groupId?.let { gid ->
            groupMemberService.save(StudentGroupMember().apply {
                groupId = gid
                this.userId = userId
            })
        }
        return getStudent(userId)
    }

    fun resetPassword(userId: Long): String {
        val user = userService.tryFindById(userId) ?: error("Student $userId not found")
        if (user.type != UserType.STUDENT) error("User $userId is not a student")
        return userDetailsService.resetPassword(userId)
    }

    @Transactional
    fun updateStudent(id: Long, displayName: String?, gitlabName: String?): StudentView {
        val user = userService.tryFindById(id) ?: error("Student $id not found")
        if (user.type != UserType.STUDENT) error("User $id is not a student")
        if (displayName != null) {
            // Skip the write entirely when the incoming value is blank or already matches -
            // avoids issuing a no-op UPDATE for an otherwise untouched row.
            val trimmed = displayName.trim().takeIf { it.isNotBlank() }
            if (trimmed != null && trimmed != user.displayName) {
                user.displayName = trimmed
                userService.save(user)
            }
        }
        if (gitlabName != null) {
            val trimmed = gitlabName.trim()
            val existingLink = gitlabUserService.findByUserId(id)
            if (trimmed.isEmpty()) {
                existingLink?.let { gitlabUserService.remove(it) }
            } else if (existingLink == null) {
                gitlabUserService.save(GitlabUser().apply {
                    this.userId = id
                    this.gitLabName = trimmed
                })
            } else {
                existingLink.gitLabName = trimmed
                gitlabUserService.save(existingLink)
            }
        }
        return getStudent(id)
    }

    @Transactional
    fun addToGroup(userId: Long, groupId: Long) {
        if (groupMemberService.findByGroupAndUser(groupId, userId) != null) return
        groupMemberService.save(StudentGroupMember().apply {
            this.groupId = groupId
            this.userId = userId
        })
    }

    @Transactional
    fun removeFromGroup(userId: Long, groupId: Long) {
        groupMemberService.findByGroupAndUser(groupId, userId)?.let { groupMemberService.remove(it) }
    }

    @Transactional
    fun deleteStudent(id: Long) {
        val user = userService.tryFindById(id) ?: error("Student $id not found")
        if (user.type != UserType.STUDENT) error("User $id is not a student")
        userService.remove(user)
    }

    private fun User.toView(
        gitlabByUserId: Map<Long?, GitlabUser>,
        allMembers: List<StudentGroupMember>,
        groupsById: Map<Long?, com.nelos.parallel.pipeline.data.entity.StudentGroup>,
        submissionCounts: Map<Long?, Int>,
    ): StudentView {
        val myMembers = allMembers.filter { it.userId == id }
        val groupIds = myMembers.mapNotNull { it.groupId }
        val groupNames = groupIds.mapNotNull { groupsById[it]?.name }
        val initial = properties?.initialPassword
        val pwdStatus = if (initial != null) "INITIAL" else "CHANGED"
        return StudentView(
            id = id,
            login = login,
            displayName = displayName,
            gitlabName = gitlabByUserId[id]?.gitLabName,
            groupIds = groupIds,
            groupNames = groupNames,
            submissionCount = submissionCounts[id] ?: 0,
            passwordStatus = pwdStatus,
            initialPassword = initial,
        )
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(StudentViewService::class.java)

        /** Synthetic group id meaning "students without any group membership". */
        const val STUDENTS_WITHOUT_GROUP: Long = -1L
    }
}
