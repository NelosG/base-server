package com.nelos.parallel.commons.entity

import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Transient
import java.io.Serializable

/**
 * Base entity for pure relation (join / link) tables that have **no surrogate id** -
 * primary key is composed of the foreign-key columns themselves. Subclasses declare
 * each PK component with `@Id @Column(name = ...)` and pair the entity with a small
 * data-class id type via `@IdClass(...::class)`.
 *
 * Provides common [equals] / [hashCode] based on the abstract [compositeKey] -
 * Hibernate uses these for proper detached-entity comparison.
 *
 * @param ID composite-key type (the `@IdClass`); must be serializable and value-comparable
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@MappedSuperclass
abstract class RelationEntity<ID : Serializable> : Entity, Serializable {

    /**
     * Returns the composite key value of this entity, or `null` for not-yet-persisted
     * entities (their PK fields haven't been set). Used by [equals] / [hashCode].
     */
    @Transient
    abstract fun compositeKey(): ID?

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val key = compositeKey() ?: return false
        val otherKey = (other as RelationEntity<*>).compositeKey() ?: return false
        return key == otherKey
    }

    override fun hashCode(): Int = compositeKey()?.hashCode() ?: 0
}
