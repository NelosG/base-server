package com.nelos.parallel.pipeline.data.entity

import com.nelos.parallel.commons.entity.AbstractEntity
import jakarta.persistence.*

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Entity(name = StudentGroup.TABLE_NAME)
@Table(name = StudentGroup.TABLE_NAME)
class StudentGroup : AbstractEntity() {

    @Id
    @Column(name = ID)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "${TABLE_NAME}_seq")
    @SequenceGenerator(name = "${TABLE_NAME}_seq", sequenceName = "seq_${TABLE_NAME}", allocationSize = 1)
    override fun getId(): Long? = id

    @get:Column(name = "name")
    var name: String? = null

    @get:Column(name = "description")
    var description: String? = null

    companion object {
        const val TABLE_NAME = "prl_student_group"
    }
}
