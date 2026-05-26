package com.nelos.parallel.adapters.http.controllers

import com.nelos.parallel.commons.adapter.vo.request.ProgressEvent
import com.nelos.parallel.pipeline.commons.service.PipelineService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class PipelineProgressControllerTest {

    private val pipelineService: PipelineService = mock()
    private val controller = PipelineProgressController(pipelineService)

    @Test
    fun `delegates the progress event to pipeline and answers 200`() {
        val event = ProgressEvent(jobId = "job-1", phase = "build", progress = 0.42)

        val response = controller.onProgress(event)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(pipelineService).handleProgress(event)
    }

    @Test
    fun `still answers 200 when pipeline handler throws - the runner must not retry`() {
        val event = ProgressEvent(jobId = "job-1", phase = "build")
        whenever(pipelineService.handleProgress(event)).doThrow(RuntimeException("db down"))

        val response = controller.onProgress(event)

        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
