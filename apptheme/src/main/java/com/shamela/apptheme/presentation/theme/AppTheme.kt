package com.shamela.apptheme.presentation.theme

import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.SideEffect
import android.content.ContextWrapper
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.shamela.apptheme.presentation.theme.colors.AppColors
import com.shamela.apptheme.presentation.theme.colors.Golden


object AppTheme {
    const val DEFAULT = "تلقائي"
    const val LIGHT = "فاتح"
    const val DARK = "مظلم"


    private val selectedColorScheme = mutableStateOf(Golden.lightColorScheme)
    private val preferredTheme = mutableStateOf(DEFAULT)

    fun changeColorScheme(newColorScheme: ColorScheme, newPreferredTheme: String) {
        selectedColorScheme.value = newColorScheme
        preferredTheme.value = newPreferredTheme
    }

    val colorScheme by derivedStateOf {
        selectedColorScheme.value
    }

    private val theme by derivedStateOf {
        preferredTheme.value
    }

    private val availableThemes = setOf(DEFAULT, LIGHT, DARK)
    fun getAvailableThemes(): Set<String> = availableThemes

    fun themeOf(
        theme: String,
        colorScheme: String,
        isSystemInDarkTheme: Boolean,
        context: Context,
    ): ColorScheme {
        val darkTheme = when (theme) {
            LIGHT -> false
            DARK -> true
            DEFAULT -> isSystemInDarkTheme
            else -> false
        }

        return AppColors.colorSchemeOf(colorScheme, context).run {
            if (darkTheme) darkColorScheme else lightColorScheme
        }
    }

    fun isDarkTheme(context: Context): Boolean {
        val nightModeFlags =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isSystemInDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        return (theme == DEFAULT && isSystemInDarkTheme) || theme == DARK
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun ShamelaLibraryTheme(
        content: @Composable () -> Unit,
    ) {
        SystemBarsAppearance(colorScheme.surface)
        // M3 Expressive: spring-based expressive motion for every Material component.
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = AppFonts.Typography,
            content = content
        )
    }

    /**
     * Status/navigation bar icons follow the theme: dark icons on light surfaces, light icons on
     * dark ones (the activities used to force light icons, which vanished on the light theme).
     */
    @Composable
    private fun SystemBarsAppearance(surface: Color) {
        val view = LocalView.current
        if (view.isInEditMode) return
        val lightSurface = surface.luminance() > 0.5f
        SideEffect {
            val window = (view.context.findActivity() ?: return@SideEffect).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightSurface
                isAppearanceLightNavigationBars = lightSurface
            }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
