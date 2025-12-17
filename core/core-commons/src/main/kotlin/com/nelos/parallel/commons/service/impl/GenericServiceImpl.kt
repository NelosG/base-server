package com.nelos.parallel.commons.service.impl

import com.nelos.parallel.commons.dao.GenericDao
import com.nelos.parallel.commons.entity.AbstractEntity
import com.nelos.parallel.commons.service.GenericService
import com.nelos.parallel.commons.service.TransactionalProcessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Stream

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
abstract class GenericServiceImpl<T : AbstractEntity, DAO : GenericDao<T>> :
    ServiceImpl<T, DAO>(), GenericService<T> {

    @Autowired
    protected lateinit var transactionalProcessor: TransactionalProcessor

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun findById(id: Long): T {
        return invokeDaoMethod { it.findById(id) }
    }

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    override fun tryFindById(id: Long): T? {
        return invokeDaoMethod { it.tryFindById(id) }
    }

    override fun findAsStream(batchSize: Int): Stream<T> {
        var lastId = 0L
        return Stream.generate {
            val res = transactionalProcessor.process<List<T>> {
                invokeDaoMethod {
                    it.findByCondition(batchSize) { cb, cq, root ->
                        cq.orderBy(
                            cb.asc(
                                root.get<String>(AbstractEntity.ID)
                            )
                        )

                        cb.greaterThan(
                            root.get(AbstractEntity.ID),
                            lastId
                        )
                    }
                }
            }
            lastId = res.maxOfOrNull { it.id ?: error("entity id is null") } ?: Long.MAX_VALUE
            res
        }.takeWhile {
            it.isNotEmpty()
        }.flatMap {
            it.stream()
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun remove(entity: T) {
        remove(entity.id ?: error("entity id is null"))
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun remove(ids: List<Long>) {
        return ids.forEach(::remove)
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun remove(id: Long) {
        return invokeDaoMethod { it.remove(id) }
    }
}
