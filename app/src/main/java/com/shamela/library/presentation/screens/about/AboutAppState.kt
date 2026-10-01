package com.shamela.library.presentation.screens.about

import androidx.annotation.StringRes

data class AboutAppState(
    val currentVersion: String = "",
    val latestVersion: String = "",
    val releaseNotes: String = "",
    val isLoadingLatestVersion: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    @StringRes val error: Int? = null,
)

val AboutAppState.updateAvailable: Boolean
    get() = latestVersion.isNotEmpty() && VersionComparator.isNewer(latestVersion, currentVersion)

/** Compares dotted numeric versions ("1.10" > "1.9"); non-numeric parts compare as 0. */
object VersionComparator {
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
}
