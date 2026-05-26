package com.nelos.parallel.adapters.http.controllers

import com.nelos.parallel.commons.adapter.listener.TaskResultListenerRegistry
import com.nelos.parallel.commons.adapter.vo.response.TaskResult
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
class HttpTaskResultCallbackControllerTest {

    private val pipelineService: PipelineService = mock()
    private val listenerRegistry: TaskResultListenerRegistry = mock()
    private val controller = HttpTaskResultCallbackController(pipelineService, listenerRegistry)

    @Test
    fun `forwards the result to pipeline and dispatches to listeners`() {
        val result = TaskResult(jobId = "job-1", nodeId = "n1", status = "completed")

        val response = controller.onResult(result)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(pipelineService).handleResult(result)
        verify(listenerRegistry).dispatch(result)
    }

    @Test
    fun `swallows listener dispatch exceptions so the engine sees a 200`() {
        val result = TaskResult(jobId = "job-1", status = "failed")
        whenever(listenerRegistry.dispatch(result)).doThrow(RuntimeException("listener boom"))

        val response = controller.onResult(result)

        // Pipeline still updated, response still OK - adapter-test UI is best-effort.
        assertEquals(HttpStatus.OK, response.statusCode)
        verify(pipelineService).handleResult(result)
    }
}
