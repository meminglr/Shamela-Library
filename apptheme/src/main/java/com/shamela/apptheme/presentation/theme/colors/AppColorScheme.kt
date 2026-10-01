package com.shamela.apptheme.presentation.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/**
 * A selectable app palette. [name] is the stable key stored in SharedPreferences (do not change it;
 * the localized label comes from PreferenceLabels).
 */
sealed class AppColorScheme(
    val name: String = "",
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme,
) {
    /**
     * Builds complete light and dark schemes (surfaces, containers, outlines, error, ...) from a
     * single seed, so every Material component gets a color that belongs to the palette instead of
     * falling back to the default purple baseline.
     */
    constructor(name: String, seed: Color, style: PaletteStyle = PaletteStyle.TonalSpot) : this(
        name = name,
        lightColorScheme = dynamicColorScheme(seedColor = seed, isDark = false, isAmoled = false, style = style),
        darkColorScheme = dynamicColorScheme(seedColor = seed, isDark = true, isAmoled = false, style = style),
    )
}
