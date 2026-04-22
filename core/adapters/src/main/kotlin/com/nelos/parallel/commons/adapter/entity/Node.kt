package com.nelos.parallel.commons.adapter.entity

import com.nelos.parallel.commons.adapter.vo.response.NodeCapabilities
import com.nelos.parallel.commons.adapter.vo.response.ResourceProviderInfo
import com.nelos.parallel.commons.adapter.vo.response.TransportInfo
import com.nelos.parallel.commons.entity.AbstractEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Persistent registration of a test-runner node.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Entity(name = Node.TABLE_NAME)
@Table(name = Node.TABLE_NAME)
class Node : AbstractEntity() {

    @Id
    @Column(name = ID)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "${TABLE_NAME}_seq")
    @SequenceGenerator(name = "${TABLE_NAME}_seq", sequenceName = "seq_${TABLE_NAME}", allocationSize = 1)
    override fun getId(): Long? = id

    @get:Column(name = "node_id")
    var nodeId: String? = null

    @get:Column(name = "capabilities")
    @get:JdbcTypeCode(SqlTypes.JSON)
    var capabilities: NodeCapabilities? = null

    @get:Column(name = "transports")
    @get:JdbcTypeCode(SqlTypes.JSON)
    var transports: List<TransportInfo>? = null

    @get:Column(name = "resource_providers")
    @get:JdbcTypeCode(SqlTypes.JSON)
    var resourceProviders: List<ResourceProviderInfo>? = null

    @get:Column(name = "registered_at")
    var registeredAt: Instant? = null

    companion object {
        const val TABLE_NAME = "prl_node"
    }
}
