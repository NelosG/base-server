package com.nelos.parallel.auth.controller

import com.nelos.parallel.auth.service.UserDetailsProviderService
import com.nelos.parallel.auth.vo.SignData
import com.nelos.parallel.auth.vo.UserVo
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Controller
class AdminController @Autowired constructor(
    private val service: UserDetailsProviderService,
) {

    @ModelAttribute("data")
    fun userRegistrationDto(): SignData {
        return SignData()
    }

    @RequestMapping("/admin")
    fun admin(): String {
        return "admin"
    }

    @PostMapping("/create-admin")
    fun createAdmin(@ModelAttribute("data") data: SignData): ResponseEntity<*> {
        val createdUser = service.signUp(data, true)
        return ResponseEntity.created(
            URI.create("/users/" + createdUser.login + "/profile")
        ).body(
            UserVo(
                login = createdUser.login ?: error("Login can't be null"),
            )
        )
    }
}