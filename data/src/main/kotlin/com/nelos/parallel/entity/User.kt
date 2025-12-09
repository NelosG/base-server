package com.nelos.parallel.entity

import com.nelos.parallel.core.entity.AbstractEntity
import com.nelos.parallel.enums.UserType
import jakarta.persistence.*
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Entity(name = User.TABLE_NAME)
@Table(name = User.TABLE_NAME)
class User : AbstractEntity(), UserDetails {

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

    @Transient
    override fun getAuthorities(): List<GrantedAuthority> {
        return type?.getRoles()?.map {
            SimpleGrantedAuthority(it)
        } ?: error("Null type")
    }

    @Transient
    override fun getPassword(): String? {
        return encryptedPassword
    }

    @Transient
    override fun getUsername(): String? {
        return login
    }

    companion object {
        const val TABLE_NAME = "prl_user"
    }
}