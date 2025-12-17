package com.nelos.parallel.entity

import com.nelos.parallel.commons.entity.AbstractEntity
import com.nelos.parallel.enums.UserType
import jakarta.persistence.*

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

    @get:Column(name = "type")
    var type: UserType? = UserType.USER

    companion object {
        const val TABLE_NAME = "prl_user"
    }
}