package com.nelos.parallel.gitlab.entity

import com.nelos.parallel.commons.entity.RelationEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 1:1 mapping between [com.nelos.parallel.auth.entity.User] and a GitLab account name.
 * No surrogate id - `user_id` is both the foreign key and the primary key. The
 * `gitlab_name` column is `UNIQUE` so a single GitLab account cannot be linked to
 * more than one orchestrator user.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Entity(name = GitlabUser.TABLE_NAME)
@Table(name = GitlabUser.TABLE_NAME)
class GitlabUser : RelationEntity<Long>() {

    @get:Id
    @get:Column(name = "user_id")
    var userId: Long? = null

    @get:Column(name = "gitlab_name")
    var gitLabName: String? = null

    override fun compositeKey(): Long? = userId

    companion object {
        const val TABLE_NAME = "prl_gitlab_user"
    }
}
