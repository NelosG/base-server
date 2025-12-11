package com.nelos.parallel.auth.vo

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.nelos.parallel.enums.UserType
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class UserData @JsonCreator constructor(
    val id: Long,
    val login: String,
    val encryptedPassword: String,
    val type: UserType,
) : UserDetails {

    override fun getAuthorities(): List<GrantedAuthority> {
        return type.getRoles().map {
            SimpleGrantedAuthority(it)
        }
    }

    override fun getPassword(): String {
        return encryptedPassword
    }

    override fun getUsername(): String {
        return login
    }
}