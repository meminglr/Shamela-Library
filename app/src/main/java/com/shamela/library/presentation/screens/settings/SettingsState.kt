package com.shamela.library.presentation.screens.settings

import android.net.Uri
import androidx.annotation.StringRes


data class SettingsState(
    val selectedViewType: SettingsViewType = SettingsViewType.Preferences,
    val isLoading: Boolean = false,
    val fileUri:Uri? = null ,
    val fileName:String? = null,
    val downloadedBooksCount: Int = 0,
    val downloadedBytes: Long = 0L,
)

/** A one-off message for a Toast, formatted with [args] when shown. */
data class ToastMessage(@StringRes val res: Int, val args: List<Any> = emptyList())
