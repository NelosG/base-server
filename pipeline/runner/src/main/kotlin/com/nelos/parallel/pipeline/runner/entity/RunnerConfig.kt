package com.nelos.parallel.pipeline.runner.entity

import com.fasterxml.jackson.databind.node.ObjectNode
import com.nelos.parallel.commons.entity.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Per-runner row holding the dispatch ordering and a free-form settings JSON
 * blob. The column shape stays the same regardless of runner type; each
 * runner deserializes its own subset of the JSON keys (LocalRunnerSettings,
 * DockerRunnerSettings, ...). HTTP and AMQP runners have no extra settings
 * (they read live state from `prl_node`) - their `settings` column stays
 * NULL.
 *
 * Source of truth is the admin UI (/runners). Application boots with an
 * empty table and the runner manager treats every `TaskRunner` bean as
 * disabled until the admin enables it.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Entity(name = RunnerConfig.TABLE_NAME)
@Table(name = RunnerConfig.TABLE_NAME)
class RunnerConfig : AbstractEntity() {

    @Id
    @Column(name = ID)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "${TABLE_NAME}_seq")
    @SequenceGenerator(name = "${TABLE_NAME}_seq", sequenceName = "seq_${TABLE_NAME}", allocationSize = 1)
    override fun getId(): Long? = id

    @get:Column(name = "name")
    var name: String? = null

    @get:Column(name = "enabled")
    var enabled: Boolean = false

    @get:Column(name = "priority")
    var priority: Int = 0

    @get:Column(name = "settings")
    @get:JdbcTypeCode(SqlTypes.JSON)
    var settings: ObjectNode? = null

    companion object {
        const val TABLE_NAME = "prl_runner_config"
    }
}
