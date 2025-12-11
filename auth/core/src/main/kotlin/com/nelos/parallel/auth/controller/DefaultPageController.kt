package com.nelos.parallel.auth.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Controller
class DefaultPageController {

    @RequestMapping("/")
    fun admin(): String {
        return "index"
    }
}