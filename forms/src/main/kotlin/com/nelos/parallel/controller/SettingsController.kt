package com.nelos.parallel.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

/**
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@Controller
class SettingsController {

    @RequestMapping("/settings")
    fun settings(): String {
        return "settings"
    }
}