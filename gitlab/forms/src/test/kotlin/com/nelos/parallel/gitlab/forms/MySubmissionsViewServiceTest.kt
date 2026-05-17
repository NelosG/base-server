package com.nelos.parallel.gitlab.forms

import com.nelos.parallel.auth.entity.User
import com.nelos.parallel.auth.service.UserService
import com.nelos.parallel.commons.security.AppRole
import com.nelos.parallel.pipeline.commons.enums.SubmissionStatus
import com.nelos.parallel.pipeline.data.entity.Submission
import com.nelos.parallel.pipeline.data.service.AssignmentService
import com.nelos.parallel.pipeline.data.service.SubmissionService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Authorization tests for the student-scoped submissions view. The "forbidden
 * for other users" path is security-critical - these tests guard against
 * accidentally exposing one student's MR/log/commit data to another.
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
class MySubmissionsViewServiceTest {

    private val submissionService: SubmissionService = mock()
    private val assignmentService: AssignmentService = mock()
    private val userService: UserService = mock()

    // The detail-building helper is not the unit under test - we provide a
    // real instance so `buildDetail` can be invoked; its own behaviour is
    // covered separately in SubmissionViewServiceTest.
    private val submissionViewService = SubmissionViewService(
        submissionService, mock(), assignmentService, userService, mock(), "",
    )
    private val service = MySubmissionsViewService(
        submissionService, assignmentService, userService, submissionViewService,
    )

    @BeforeEach
    fun authenticate() {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(LOGIN, null, AppRole.ROLE_STUDENT)
    }

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    private fun student(id: Long = MY_USER_ID, login: String = LOGIN) = User().apply {
        this.id = id
        this.login = login
    }

    private fun submission(id: Long, ownerId: Long): Submission = Submission().apply {
        this.id = id
        this.userId = ownerId
        this.status = SubmissionStatus.COMPLETED
    }

    @Test
    fun `getMySubmission refuses to return another student's submission`() {
        whenever(userService.findByLogin(LOGIN)).thenReturn(student())
        whenever(submissionService.tryFindById(MY_SUBMISSION_ID))
            .thenReturn(submission(MY_SUBMISSION_ID, ownerId = OTHER_USER_ID))

        val ex = assertThrows<IllegalStateException> { service.getMySubmission(MY_SUBMISSION_ID) }
        assertTrue(ex.message?.contains("Forbidden") == true)
    }

    @Test
    fun `getMySubmission returns the detail when the submission belongs to the current user`() {
        whenever(userService.findByLogin(LOGIN)).thenReturn(student())
        whenever(submissionService.tryFindById(MY_SUBMISSION_ID))
            .thenReturn(submission(MY_SUBMISSION_ID, ownerId = MY_USER_ID))
        whenever(assignmentService.tryFindById(any())).thenReturn(null)

        val detail = service.getMySubmission(MY_SUBMISSION_ID)

        assertEquals(MY_SUBMISSION_ID, detail.id)
    }

    @Test
    fun `getMySubmission errors when the submission does not exist`() {
        val unknownId = 404L
        whenever(userService.findByLogin(LOGIN)).thenReturn(student())
        whenever(submissionService.tryFindById(unknownId)).thenReturn(null)

        assertThrows<IllegalStateException> { service.getMySubmission(unknownId) }
    }

    @Test
    fun `unauthenticated context is rejected`() {
        SecurityContextHolder.clearContext()

        assertThrows<IllegalStateException> { service.getMySubmissions() }
        verify(submissionService, never()).findByUserId(any())
    }

    @Test
    fun `current user disappeared between auth and lookup raises an error`() {
        whenever(userService.findByLogin(LOGIN)).thenReturn(null)

        assertThrows<IllegalStateException> { service.getMySubmissions() }
    }

    companion object {
        private const val LOGIN = "alice"
        private const val MY_USER_ID = 11L
        private const val OTHER_USER_ID = 99L
        private const val MY_SUBMISSION_ID = 42L
    }
}
