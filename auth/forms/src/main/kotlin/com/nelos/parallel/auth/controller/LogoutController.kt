package com.nelos.parallel.auth.controller

import com.nelos.parallel.auth.vo.UserData
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping


/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Controller
class LogoutController {

    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal user: UserData): ResponseEntity<Unit> {
        SecurityContextHolder.clearContext()
        return ResponseEntity.noContent().build()
    }
}
