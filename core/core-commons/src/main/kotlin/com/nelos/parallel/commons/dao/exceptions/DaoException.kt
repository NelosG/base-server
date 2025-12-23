package com.nelos.parallel.commons.dao.exceptions

/**
 * Exception thrown when a DAO operation fails.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class DaoException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)