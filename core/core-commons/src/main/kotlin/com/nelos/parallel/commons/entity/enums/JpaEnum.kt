package com.nelos.parallel.commons.entity.enums

/**
 * Interface for enums that are persisted via JPA using a custom database key.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface JpaEnum {

    /**
     * Returns the database key used to store this enum value.
     */
    fun getDbKey(): String
}