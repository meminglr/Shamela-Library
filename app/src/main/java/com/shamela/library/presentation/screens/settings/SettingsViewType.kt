package com.shamela.library.presentation.screens.settings

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.R

enum class SettingsViewType(@StringRes val label: Int) {
    Preferences(label = R.string.prefernces),
    General(label = R.string.settings_general);

    val icon: ImageVector
        get() = when (this) {
            Preferences -> ShamelaIcons.Palette
            General -> ShamelaIcons.Tune
        }
}
