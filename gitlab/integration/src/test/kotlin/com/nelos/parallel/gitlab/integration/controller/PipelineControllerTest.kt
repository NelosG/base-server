package com.nelos.parallel.gitlab.integration.controller

import com.nelos.parallel.pipeline.commons.service.PipelineService
import com.nelos.parallel.pipeline.commons.vo.PipelineStatusResponse
import com.nelos.parallel.pipeline.commons.vo.PipelineSubmitRequest
import com.nelos.parallel.pipeline.commons.vo.PipelineSubmitResponse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class PipelineControllerTest {

    private val pipelineService: PipelineService = mock()
    private val controller = PipelineController(pipelineService)

    @Test
    fun `submit returns 200 with the submission id and status from the pipeline service`() {
        val request = PipelineSubmitRequest(
            projectPath = "root/lab1",
            mrIid = 7L,
            sourceBranch = "feature",
            commitSha = "abc",
            username = "alice",
        )
        whenever(pipelineService.submit(request))
            .thenReturn(PipelineSubmitResponse(submissionId = 42L, status = "queued"))

        val response = controller.submit(request)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(42L, response.body?.submissionId)
        assertEquals("queued", response.body?.status)
    }

    @Test
    fun `submit returns 400 with a synthetic error body when pipeline service throws`() {
        // Treat the CI script as a dumb consumer: we never want a 500 here
        // because GitLab would silently mark the job failed - the error message
        // must end up in the CI job log, so we package it into the body.
        val request = PipelineSubmitRequest(projectPath = "root/lab1", mrIid = 7L)
        whenever(pipelineService.submit(any())).doThrow(IllegalStateException("assignment not found"))

        val response = controller.submit(request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(-1L, response.body?.submissionId)
        assertTrue(response.body?.status?.contains("assignment not found") == true)
    }

    @Test
    fun `status returns 200 with the response body when found`() {
        val resp = PipelineStatusResponse(
            submissionId = 7L,
            status = "running",
            finished = false,
        )
        whenever(pipelineService.getStatus(7L)).thenReturn(resp)

        val response = controller.status(7L)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals("running", response.body?.status)
    }

    @Test
    fun `status returns 404 when the submission is unknown`() {
        // CI script must be able to distinguish "not yet queued" from "server
        // explosion": 404 lets it retry the request immediately.
        whenever(pipelineService.getStatus(eq(9999L))).doThrow(NoSuchElementException("no such submission"))

        val response = controller.status(9999L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `status doesn't fall through to submit on getStatus failure`() {
        whenever(pipelineService.getStatus(eq(1L))).doThrow(RuntimeException("db down"))

        controller.status(1L)

        verify(pipelineService, never()).submit(any())
    }
}
