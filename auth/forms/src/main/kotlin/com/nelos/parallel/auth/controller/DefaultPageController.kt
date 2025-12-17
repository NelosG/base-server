package com.nelos.parallel.auth.controller

import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Controller
class DefaultPageController {

    @RequestMapping("/")
    fun defaultPage(): String {
        val authentication = SecurityContextHolder.getContext().authentication
        return if (authentication == null || authentication is AnonymousAuthenticationToken) {
            "redirect:/login?unauthorized=true"
        } else {
            "index"
        }
    }
}