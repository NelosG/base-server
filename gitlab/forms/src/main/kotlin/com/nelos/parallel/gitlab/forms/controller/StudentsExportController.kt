package com.nelos.parallel.gitlab.forms.controller

import com.nelos.parallel.gitlab.forms.StudentViewService
import com.nelos.parallel.gitlab.forms.vo.StudentView
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Streams the student roster as a UTF-8 BOM-prefixed CSV (Excel-friendly). Includes
 * the one-time initial password for students who haven't changed it yet - admins use
 * this to hand out credentials in bulk. Authorization is via the path matcher in
 * WebSecurityConfig (ROLE_ADMIN).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@RestController("prl.studentsExportController")
@RequestMapping("/api/students")
class StudentsExportController(
    private val studentViewService: StudentViewService,
) {

    @GetMapping("/export.csv")
    fun exportCsv(@RequestParam(required = false) groupId: Long?): ResponseEntity<ByteArray> {
        val body = buildCsv(studentViewService.getStudents(groupId)).toByteArray(Charsets.UTF_8)
        val headers = HttpHeaders().apply {
            contentType = MediaType.parseMediaType("text/csv; charset=utf-8")
            setContentDispositionFormData("attachment", "students.csv")
        }
        return ResponseEntity.ok().headers(headers).body(body)
    }

    private fun buildCsv(students: List<StudentView>): String {
        val sb = StringBuilder()
        sb.append(BOM)
        sb.appendLine("login,password,displayName,gitlabName,groups,passwordStatus")
        students.forEach { s ->
            sb.appendLine(
                listOf(
                    s.login.orEmpty(),
                    s.initialPassword.orEmpty(),
                    s.displayName.orEmpty(),
                    s.gitlabName.orEmpty(),
                    (s.groupNames ?: emptyList()).joinToString("; "),
                    s.passwordStatus.orEmpty(),
                ).joinToString(",") { csvEscape(it) }
            )
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value

    companion object {
        /** UTF-8 BOM - makes Excel auto-detect the encoding when opening the file. */
        private const val BOM = '\uFEFF'
    }
}
