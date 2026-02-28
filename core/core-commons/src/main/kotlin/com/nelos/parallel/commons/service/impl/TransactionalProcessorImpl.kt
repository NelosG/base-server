package com.nelos.parallel.commons.service.impl

import com.nelos.parallel.commons.service.TransactionalProcessor
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.function.Supplier

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Primary
@Service("prl.transactionalProcessor")
class TransactionalProcessorImpl : TransactionalProcessor {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun <T> process(supplier: Supplier<T>): T {
        return supplier.get()
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun process(runnable: Runnable) {
        runnable.run()
    }
}