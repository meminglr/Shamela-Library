package com.shamela.apptheme.presentation.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/**
 * In-app language selection (Arabic / Turkish / follow the system).
 *
 * - Android 13+: uses the platform per-app language (LocaleManager), which also shows up in the
 *   system "App languages" settings and is applied to every activity and the app context.
 * - Older versions: the choice is stored in SharedPreferences and applied by wrapping each
 *   activity's (and the Application's) base context via [wrap].
 *
 * Arabic is the default resource language, so "system" on a non-Turkish device shows Arabic.
 */
object AppLocale {
    const val SYSTEM = ""
    const val ARABIC = "ar"
    const val TURKISH = "tr"
    val supported = listOf(SYSTEM, ARABIC, TURKISH)

    private const val PREFS = "app_locale"
    private const val KEY_LANGUAGE = "language"

    /** The user's explicit choice, or [SYSTEM]. */
    fun selected(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            return if (locales.isEmpty) SYSTEM else locales[0].language
        }
        return prefs(context).getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM
    }

    /**
     * Persists and applies [language]. On Android 13+ the system recreates running activities;
     * on older versions the app restarts so every screen (and the app context) picks it up.
     */
    fun select(activity: Activity, language: String) {
        if (language == selected(activity)) return
        prefs(activity).edit().putString(KEY_LANGUAGE, language).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == SYSTEM) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(language)
        } else {
            val restart = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            activity.finishAffinity()
            restart?.let { activity.startActivity(it) }
            Runtime.getRuntime().exit(0)
        }
    }

    /** Wraps [base] with the selected locale. A no-op on Android 13+ (the platform does it). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val language = prefs(base).getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM
        if (language == SYSTEM) return base
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** True when the UI is shown in Turkish (left-to-right); otherwise the UI is Arabic. */
    fun isTurkishUi(context: Context): Boolean =
        context.resources.configuration.locales[0].language == TURKISH

    /**
     * The layout direction for app chrome. Arabic strings are the fallback for every non-Turkish
     * locale, so anything but Turkish is laid out right-to-left.
     */
    fun layoutDirection(context: Context): LayoutDirection =
        if (isTurkishUi(context)) LayoutDirection.Ltr else LayoutDirection.Rtl

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
