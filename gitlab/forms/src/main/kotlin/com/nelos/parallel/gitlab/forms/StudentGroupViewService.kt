package com.nelos.parallel.gitlab.forms

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.commons.view.service.ViewService
import com.nelos.parallel.gitlab.entity.GitlabUser
import com.nelos.parallel.gitlab.forms.vo.SaveStudentGroupRequest
import com.nelos.parallel.gitlab.forms.vo.StudentGroupMemberView
import com.nelos.parallel.gitlab.forms.vo.StudentGroupView
import com.nelos.parallel.gitlab.integration.GitlabStudentResolver
import com.nelos.parallel.gitlab.service.GitlabUserService
import com.nelos.parallel.pipeline.data.entity.StudentGroup
import com.nelos.parallel.pipeline.data.entity.StudentGroupMember
import com.nelos.parallel.pipeline.data.service.StudentGroupMemberService
import com.nelos.parallel.pipeline.data.service.StudentGroupService
import org.springframework.transaction.annotation.Transactional

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.studentGroupViewService", roles = [AppRole.ADMIN])
class StudentGroupViewService(
    private val groupService: StudentGroupService,
    private val memberService: StudentGroupMemberService,
    private val userService: UserService,
    private val gitlabUserService: GitlabUserService,
    private val studentResolver: GitlabStudentResolver,
) {

    fun getGroups(): List<StudentGroupView> {
        val ctx = loadContext()
        return groupService.findAll().map { group ->
            val groupId = group.id ?: return@map group.toView(emptyList())
            group.toView(memberService.findByGroupId(groupId).map { it.toView(ctx) })
        }
    }

    fun getGroup(id: Long): StudentGroupView {
        val ctx = loadContext()
        val group = groupService.findById(id)
        val members = memberService.findByGroupId(id).map { it.toView(ctx) }
        return group.toView(members)
    }

    @Transactional
    fun saveGroup(data: SaveStudentGroupRequest): StudentGroupView {
        val group = data.id?.let { groupService.findById(it) } ?: StudentGroup()
        group.name = data.name ?: group.name
        group.description = data.description ?: group.description
        val saved = groupService.save(group)
        val savedId = saved.id ?: error("Group has no id after save")

        if (data.members != null) {
            memberService.deleteByGroupId(savedId)
            data.members.forEach { m -> addMember(savedId, m) }
        }
        return getGroup(savedId)
    }

    @Transactional
    fun deleteGroup(id: Long) {
        groupService.remove(id)
    }

    private fun addMember(groupId: Long, view: StudentGroupMemberView) {
        val user = resolveUser(view) ?: return
        val userId = user.id ?: return
        // Apply incoming displayName if it's a meaningful update (avoid overwriting with auto-default).
        view.displayName?.trim()?.takeIf { it.isNotEmpty() && it != user.displayName }?.let {
            user.displayName = it
            userService.save(user)
        }
        if (memberService.findByGroupAndUser(groupId, userId) == null) {
            memberService.save(StudentGroupMember().apply {
                this.groupId = groupId
                this.userId = userId
            })
        }
    }

    private fun resolveUser(view: StudentGroupMemberView): User? {
        view.userId?.let { id -> userService.tryFindById(id)?.let { return it } }
        val gitlabName = view.gitlabName?.takeIf { it.isNotBlank() }
            ?: view.login?.takeIf { it.isNotBlank() }
            ?: return null
        return userService.tryFindById(studentResolver.resolveOrAutoCreate(gitlabName))
    }

    private fun loadContext() = ViewContext(
        users = userService.findAll().associateBy { it.id },
        gitlabLinks = gitlabUserService.findAll().associateBy { it.userId },
    )

    private data class ViewContext(
        val users: Map<Long?, User>,
        val gitlabLinks: Map<Long?, GitlabUser>,
    )

    private fun StudentGroup.toView(members: List<StudentGroupMemberView>) = StudentGroupView(
        id = id,
        name = name,
        description = description,
        memberCount = members.size,
        members = members,
    )

    private fun StudentGroupMember.toView(ctx: ViewContext): StudentGroupMemberView {
        val user = ctx.users[userId]
        return StudentGroupMemberView(
            userId = userId,
            login = user?.login,
            displayName = user?.displayName,
            gitlabName = ctx.gitlabLinks[userId]?.gitLabName,
        )
    }
}
