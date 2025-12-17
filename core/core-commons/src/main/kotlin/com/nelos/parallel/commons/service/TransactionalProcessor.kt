package com.nelos.parallel.commons.service

import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.function.Supplier

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
interface TransactionalProcessor {

    /**
     * Create a transaction from supplier and run it.
     *
     * @param supplier supplier
     *
     * @return supplier result
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun <T> process(supplier: Supplier<T>): T

    /**
     * Create a transaction from runnable and run it.
     *
     * @param runnable runnable
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun process(runnable: Runnable)
}