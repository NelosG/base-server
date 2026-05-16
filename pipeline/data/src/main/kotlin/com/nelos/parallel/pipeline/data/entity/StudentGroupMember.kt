package com.nelos.parallel.pipeline.data.entity

import com.nelos.parallel.commons.entity.RelationEntity
import jakarta.persistence.*
import java.io.Serializable

/**
 * Pure N:M link between [StudentGroup] and [com.nelos.parallel.auth.entity.User].
 * No surrogate id - primary key is `(group_id, user_id)`.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Entity(name = StudentGroupMember.TABLE_NAME)
@Table(name = StudentGroupMember.TABLE_NAME)
@IdClass(StudentGroupMember.Pk::class)
class StudentGroupMember : RelationEntity<StudentGroupMember.Pk>() {

    @get:Id
    @get:Column(name = "group_id")
    var groupId: Long? = null

    @get:Id
    @get:Column(name = "user_id")
    var userId: Long? = null

    override fun compositeKey(): Pk = Pk(groupId, userId)

    /**
     * Composite key for [StudentGroupMember]. Field names must match the entity's
     * `@Id`-annotated property names (`groupId`, `userId`) for Hibernate's `@IdClass`
     * resolution.
     */
    data class Pk(
        var groupId: Long? = null,
        var userId: Long? = null,
    ) : Serializable

    companion object {
        const val TABLE_NAME = "prl_student_group_member"
    }
}
