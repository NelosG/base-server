package com.nelos.parallel.commons.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass

/**
 * Base entity with code, name, and description fields for dictionary-like entities.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@MappedSuperclass
abstract class CodeEntity : AbstractEntity() {

    @get:Column(name = "code")
    var code: String? = null

    @get:Column(name = "name")
    var name: String? = null

    @get:Column(name = "description")
    var description: String? = null
}
