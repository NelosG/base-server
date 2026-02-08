package com.nelos.parallel.git.vo

import com.nelos.parallel.git.vo.Repository.Companion.builder


/**
 * Value object describing a Git repository to clone, including credentials.
 * Use [builder] to create instances.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class Repository internal constructor(
    val uri: String,
    val branch: String,
    val token: String?,
    val userName: String?,
    val password: String?,
) {
    class Builder internal constructor() {
        private var uri: String? = null
        private var branch: String = "master"
        private var token: String? = null
        private var userName: String? = null
        private var password: String? = null

        fun uri(uri: String) = apply { this.uri = uri }
        fun branch(branch: String) = apply { this.branch = branch }
        fun token(token: String) = apply { this.token = token }
        fun userName(userName: String) = apply { this.userName = userName }
        fun password(password: String) = apply { this.password = password }

        fun build(): Repository {
            val resolvedUri = uri ?: error("Repository URI must be set")

            if (token == null) {
                userName ?: error("No token or userName provided")
                password ?: error("No password provided")
            }

            return Repository(resolvedUri, branch, token, userName, password)
        }
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }
}