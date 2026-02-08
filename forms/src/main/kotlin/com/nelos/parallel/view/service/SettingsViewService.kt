package com.nelos.parallel.view.service

import com.nelos.parallel.commons.view.service.ViewService
import com.nelos.parallel.view.enums.Language
import com.nelos.parallel.view.enums.Theme
import com.nelos.parallel.view.vo.EnumOptionView
import com.nelos.parallel.view.vo.SettingsView

/**
 * View service for managing user settings (theme, language, notifications).
 *
 * @author gpushkarev
 * @since %CURRENT_VERSION%
 */
@ViewService("prl.settingsViewService")
class SettingsViewService {

    @Volatile
    private var settings = SettingsView(
        theme = Theme.DARK,
        language = Language.EN,
        notificationsEnabled = true,
    )

    fun getThemes(): List<EnumOptionView> = Theme.entries.map {
        EnumOptionView(it.key, it.label)
    }

    fun getLanguages(): List<EnumOptionView> = Language.entries.map {
        EnumOptionView(it.key, it.label)
    }

    fun getSettings(): SettingsView = settings

    fun saveSettings(settings: SettingsView): SettingsView =
        settings.also { this.settings = it }
}