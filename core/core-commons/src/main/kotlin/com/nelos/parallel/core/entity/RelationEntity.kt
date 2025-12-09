package com.nelos.parallel.core.entity

import jakarta.persistence.MappedSuperclass
import java.io.Serializable

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@MappedSuperclass
abstract class RelationEntity : Entity, Serializable