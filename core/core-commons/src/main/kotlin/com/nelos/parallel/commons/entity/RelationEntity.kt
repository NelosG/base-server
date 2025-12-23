package com.nelos.parallel.commons.entity

import jakarta.persistence.MappedSuperclass
import java.io.Serializable

/**
 * Base entity for relation (join) tables without a generated ID.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@MappedSuperclass
abstract class RelationEntity : Entity, Serializable