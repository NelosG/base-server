package com.nelos.parallel.auth.entity

import com.nelos.parallel.commons.entity.AbstractEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * API key for authenticating test-runner nodes.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Entity(name = ApiKey.TABLE_NAME)
@Table(name = ApiKey.TABLE_NAME)
class ApiKey : AbstractEntity() {

    @Id
    @Column(name = ID)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "${TABLE_NAME}_seq")
    @SequenceGenerator(name = "${TABLE_NAME}_seq", sequenceName = "seq_${TABLE_NAME}", allocationSize = 1)
    override fun getId(): Long? = id

    @get:Column(name = "key_hash")
    var keyHash: String? = null

    @get:Column(name = "key_prefix")
    var keyPrefix: String? = null

    @get:Column(name = "name")
    var name: String? = null

    @get:Column(name = "active")
    var active: Boolean = true

    @get:Column(name = "created_at")
    var createdAt: Instant = Instant.now()

    companion object {
        const val TABLE_NAME = "prl_api_key"
    }
}
