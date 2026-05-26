package com.nelos.parallel.pipeline.core.service.impl

import com.nelos.parallel.pipeline.data.entity.SubmissionLog
import com.nelos.parallel.pipeline.data.service.SubmissionLogService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import kotlin.test.assertEquals

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class SubmissionLoggerTest {

    private val service: SubmissionLogService = mock()
    private val logger = SubmissionLogger(service)

    @Test
    fun `appendOne saves exactly one row with the line text`() {
        logger.appendOne(7L, "hello")

        val captor = argumentCaptor<List<SubmissionLog>>()
        verify(service).save(captor.capture())
        val entities = captor.firstValue
        assertEquals(1, entities.size)
        assertEquals(7L, entities[0].submissionId)
        assertEquals("hello", entities[0].line)
    }

    @Test
    fun `append on empty list is a no-op - we never call save with an empty batch`() {
        logger.append(7L, emptyList())

        verify(service, never()).save(any<Collection<SubmissionLog>>())
    }

    @Test
    fun `append assigns the same timestamp to every line in the batch`() {
        // All lines in one append share a single LocalDateTime.now() snapshot;
        // we rely on the table's auto-increment id for sort order, not on the
        // timestamp, so equal timestamps are intentional.
        logger.append(7L, listOf("a", "b", "c"))

        val captor = argumentCaptor<List<SubmissionLog>>()
        verify(service).save(captor.capture())
        val entities = captor.firstValue
        assertEquals(3, entities.size)
        val timestamps = entities.map { it.createdAt }.toSet()
        assertEquals(1, timestamps.size)
        assertEquals(listOf("a", "b", "c"), entities.map { it.line })
        assertEquals(listOf(7L, 7L, 7L), entities.map { it.submissionId })
    }
}
