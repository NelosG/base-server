package com.nelos.parallel.commons.service

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Helper for side-effects that must fire only when the surrounding transaction
 * actually commits. Typical use case: invalidating an in-process cache after a
 * DB write - if we invalidate before commit, a concurrent reader can race in,
 * read the pre-commit state from DB, and repopulate the cache with the
 * about-to-be-stale value; or the transaction rolls back and the cache ends
 * up empty for no reason.
 *
 * If no transaction is currently active (e.g. the caller wasn't `@Transactional`),
 * the [action] runs immediately - preserving the caller's contract.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
object TxAfterCommit {

    fun runAfterCommit(action: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    action()
                }
            })
        } else {
            action()
        }
    }
}
