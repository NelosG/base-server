package com.nelos.parallel.gitlab.forms

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.commons.view.vo.PageRequest
import com.nelos.parallel.gitlab.service.GitlabUserService
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.data.entity.Assignment
import com.nelos.parallel.pipeline.data.entity.Submission
import com.nelos.parallel.pipeline.data.service.AssignmentService
import com.nelos.parallel.pipeline.data.service.SubmissionResultService
import com.nelos.parallel.pipeline.data.service.SubmissionService
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class SubmissionViewServiceTest {

    private val submissionService: SubmissionService = mock()
    private val submissionResultService: SubmissionResultService = mock()
    private val assignmentService: AssignmentService = mock()
    private val userService: UserService = mock()
    private val gitlabUserService: GitlabUserService = mock()

    private fun service(gitlabUrl: String = "https://gitlab.example.com") =
        SubmissionViewService(
            submissionService, submissionResultService, assignmentService,
            userService, gitlabUserService, gitlabUrl,
        )

    private fun assignment(id: Long = 1L, gitlabProjectPath: String? = "course/lab1"): Assignment =
        Assignment().apply {
            this.id = id
            this.code = "lab1"
            this.name = "Lab 1"
            this.gitlabProjectPath = gitlabProjectPath
        }

    private fun submission(id: Long, mrIid: Long? = 7L, assignmentId: Long? = 1L): Submission =
        Submission().apply {
            this.id = id
            this.mrIid = mrIid
            this.assignmentId = assignmentId
            this.status = SubmissionStatus.COMPLETED
        }

    // --- pagination - limit+1 hasMore probe + clamp ---------------------

    @Nested
    inner class Pagination {

        @Test
        fun `requested limit triggers a limit+1 fetch and reports hasMore=true when the extra row appears`() {
            val svc = service()
            // limit = 2 -> service asks the DAO for 3 to probe "is there more".
            whenever(submissionService.findFilteredPage(anyOrNull(), anyOrNull(), eq(0), eq(3)))
                .thenReturn(listOf(submission(1L), submission(2L), submission(3L)))
            whenever(userService.findAll()).thenReturn(emptyList<User>())
            whenever(assignmentService.findAll()).thenReturn(listOf(assignment()))

            val page = svc.getSubmissions(userId = null, assignmentId = null, page = PageRequest(offset = 0, limit = 2))

            assertEquals(2, page.items.size, "extra probe row must be dropped from items")
            assertTrue(page.hasMore)
        }

        @Test
        fun `last page reports hasMore=false`() {
            val svc = service()
            whenever(submissionService.findFilteredPage(anyOrNull(), anyOrNull(), eq(0), eq(3)))
                .thenReturn(listOf(submission(1L), submission(2L))) // only 2 rows, less than limit+1
            whenever(userService.findAll()).thenReturn(emptyList<User>())
            whenever(assignmentService.findAll()).thenReturn(listOf(assignment()))

            val page = svc.getSubmissions(userId = null, assignmentId = null, page = PageRequest(offset = 0, limit = 2))

            assertEquals(2, page.items.size)
            assertFalse(page.hasMore)
        }

        @Test
        fun `requesting a limit above MAX_PAGE_SIZE clamps to 200`() {
            val svc = service()
            // The service must call findFilteredPage with limit = 200 + 1 = 201,
            // even though the caller asked for 5000.
            whenever(submissionService.findFilteredPage(anyOrNull(), anyOrNull(), any(), eq(201)))
                .thenReturn(emptyList())
            whenever(userService.findAll()).thenReturn(emptyList<User>())
            whenever(assignmentService.findAll()).thenReturn(emptyList<Assignment>())

            svc.getSubmissions(userId = null, assignmentId = null, page = PageRequest(offset = 0, limit = 5000))

            verify(submissionService).findFilteredPage(anyOrNull(), anyOrNull(), eq(0), eq(201))
        }

        @Test
        fun `requesting a limit below 1 is clamped up to 1`() {
            val svc = service()
            whenever(submissionService.findFilteredPage(anyOrNull(), anyOrNull(), eq(0), eq(2)))
                .thenReturn(emptyList())
            whenever(userService.findAll()).thenReturn(emptyList<User>())
            whenever(assignmentService.findAll()).thenReturn(emptyList<Assignment>())

            svc.getSubmissions(userId = null, assignmentId = null, page = PageRequest(offset = 0, limit = 0))

            // 0 -> 1 -> DAO call uses 1 + 1 = 2.
            verify(submissionService).findFilteredPage(anyOrNull(), anyOrNull(), eq(0), eq(2))
        }

        @Test
        fun `null PageRequest uses the default page size`() {
            val svc = service()
            // Default size = 50; probe fetch = 51.
            whenever(submissionService.findFilteredPage(anyOrNull(), anyOrNull(), eq(0), eq(51)))
                .thenReturn(emptyList())
            whenever(userService.findAll()).thenReturn(emptyList<User>())
            whenever(assignmentService.findAll()).thenReturn(emptyList<Assignment>())

            svc.getSubmissions(userId = null, assignmentId = null, page = null)

            verify(submissionService).findFilteredPage(anyOrNull(), anyOrNull(), eq(0), eq(51))
        }

        @Test
        fun `filter parameters are propagated to the DAO unchanged`() {
            val svc = service()
            whenever(submissionService.findFilteredPage(eq(7L), eq(1L), eq(20), any()))
                .thenReturn(emptyList())
            whenever(userService.findAll()).thenReturn(emptyList<User>())
            whenever(assignmentService.findAll()).thenReturn(emptyList<Assignment>())

            svc.getSubmissions(userId = 7L, assignmentId = 1L, page = PageRequest(offset = 20, limit = 10))

            verify(submissionService).findFilteredPage(eq(7L), eq(1L), eq(20), eq(11))
        }
    }

    // --- mrUrl ----------------------------------------------------------

    @Nested
    inner class MrUrlBuilding {

        @Test
        fun `builds the merge-request URL when assignment, mr iid and base url are all present`() {
            val svc = service(gitlabUrl = "https://gitlab.example.com")
            whenever(submissionService.tryFindById(42L)).thenReturn(submission(42L, mrIid = 7L))
            whenever(assignmentService.tryFindById(1L)).thenReturn(assignment(gitlabProjectPath = "course/lab1"))
            whenever(userService.tryFindById(any())).thenReturn(null)
            whenever(gitlabUserService.findByUserId(any())).thenReturn(null)
            whenever(submissionResultService.findBySubmissionId(42L)).thenReturn(null)

            val detail = svc.getSubmission(42L)

            assertEquals("https://gitlab.example.com/course/lab1/-/merge_requests/7", detail.mrUrl)
        }

        @Test
        fun `trailing slash in gitlab base URL is trimmed before composing the MR URL`() {
            val svc = service(gitlabUrl = "https://gitlab.example.com/")
            whenever(submissionService.tryFindById(42L)).thenReturn(submission(42L, mrIid = 7L))
            whenever(assignmentService.tryFindById(1L)).thenReturn(assignment())
            whenever(userService.tryFindById(any())).thenReturn(null)
            whenever(gitlabUserService.findByUserId(any())).thenReturn(null)
            whenever(submissionResultService.findBySubmissionId(42L)).thenReturn(null)

            val detail = svc.getSubmission(42L)

            // No double slash before the project path.
            assertEquals("https://gitlab.example.com/course/lab1/-/merge_requests/7", detail.mrUrl)
        }

        @Test
        fun `blank gitlab url leaves mrUrl as null (configuration absent)`() {
            val svc = service(gitlabUrl = "")
            whenever(submissionService.tryFindById(42L)).thenReturn(submission(42L))
            whenever(assignmentService.tryFindById(1L)).thenReturn(assignment())
            whenever(userService.tryFindById(any())).thenReturn(null)
            whenever(gitlabUserService.findByUserId(any())).thenReturn(null)
            whenever(submissionResultService.findBySubmissionId(42L)).thenReturn(null)

            val detail = svc.getSubmission(42L)

            assertNull(detail.mrUrl)
        }

        @Test
        fun `missing mrIid leaves mrUrl as null`() {
            val svc = service()
            whenever(submissionService.tryFindById(42L)).thenReturn(submission(42L, mrIid = null))
            whenever(assignmentService.tryFindById(1L)).thenReturn(assignment())
            whenever(userService.tryFindById(any())).thenReturn(null)
            whenever(gitlabUserService.findByUserId(any())).thenReturn(null)
            whenever(submissionResultService.findBySubmissionId(42L)).thenReturn(null)

            val detail = svc.getSubmission(42L)

            assertNull(detail.mrUrl)
        }

        @Test
        fun `missing gitlabProjectPath leaves mrUrl as null`() {
            val svc = service()
            whenever(submissionService.tryFindById(42L)).thenReturn(submission(42L, mrIid = 7L))
            whenever(assignmentService.tryFindById(1L)).thenReturn(assignment(gitlabProjectPath = null))
            whenever(userService.tryFindById(any())).thenReturn(null)
            whenever(gitlabUserService.findByUserId(any())).thenReturn(null)
            whenever(submissionResultService.findBySubmissionId(42L)).thenReturn(null)

            val detail = svc.getSubmission(42L)

            assertNull(detail.mrUrl)
        }
    }

    // --- getSubmission not-found ----------------------------------------

    @Test
    fun `unknown submission id bubbles out`() {
        val svc = service()
        whenever(submissionService.tryFindById(404L)).thenReturn(null)

        assertThrows<IllegalStateException> { svc.getSubmission(404L) }
    }
}
