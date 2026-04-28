package com.nelos.parallel.auth.entity

import com.nelos.parallel.auth.entity.properties.UserProperties
import com.nelos.parallel.auth.enums.UserType
import com.nelos.parallel.commons.entity.AbstractEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Entity(name = User.TABLE_NAME)
@Table(name = User.TABLE_NAME)
class User : AbstractEntity() {

    @Id
    @Column(name = ID)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "${TABLE_NAME}_seq")
    @SequenceGenerator(name = "${TABLE_NAME}_seq", sequenceName = "seq_${TABLE_NAME}", allocationSize = 1)
    override fun getId(): Long? = id

    @get:Column(name = "login")
    var login: String? = null

    @get:Column(name = "encrypted_password")
    var encryptedPassword: String? = null

    @get:Column(name = "display_name")
    var displayName: String? = null

    @get:Column(name = "type")
    var type: UserType? = UserType.USER

    @get:Column(name = "properties")
    @get:JdbcTypeCode(SqlTypes.JSON)
    var properties: UserProperties? = null

    companion object {
        const val TABLE_NAME = "prl_user"
    }
}
