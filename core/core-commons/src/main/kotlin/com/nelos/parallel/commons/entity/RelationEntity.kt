package com.nelos.parallel.commons.entity

import jakarta.persistence.MappedSuperclass
import java.io.Serializable

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@MappedSuperclass
abstract class RelationEntity : Entity, Serializable