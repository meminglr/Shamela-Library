package com.shamela.library.presentation.screens.settings

import androidx.annotation.StringRes
import com.shamela.library.R

enum class SettingsViewType(@StringRes val label: Int) {
    Preferences(label = R.string.prefernces),
    General(label = R.string.settings_general),
    ExternalBooks(label = R.string.external_bboks)
}
