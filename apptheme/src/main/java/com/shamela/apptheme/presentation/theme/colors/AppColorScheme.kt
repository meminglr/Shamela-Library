package com.shamela.apptheme.presentation.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * A selectable app palette. [name] is the stable key stored in SharedPreferences (do not change it;
 * the localized label comes from PreferenceLabels).
 *
 * A palette is one soft, pastel accent on its [Surfaces]: the accent marks what can be tapped or
 * is selected (buttons, the selected tab, sliders, switches), the surfaces set the mood. Things
 * drawn on the accent use the palette's dark ink, as white is too harsh on pastel. Most palettes use clean greys with a faint wash of the accent; a palette with a
 * character of its own (e.g. [Brown]'s old paper and leather) brings its own surfaces; all
 * palettes but the system one ([MaterialYou]) do.
 */
sealed class AppColorScheme(
    val name: String = "",
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme,
) {
    /** [light] and [dark]: the accent in each mode (a lighter one in dark mode). */
    constructor(
        name: String,
        light: Color,
        dark: Color,
        lightSurfaces: Surfaces = Surfaces.neutralLight(light),
        darkSurfaces: Surfaces = Surfaces.neutralDark(dark),
    ) : this(
        name = name,
        lightColorScheme = accentScheme(light, lightSurfaces, isDark = false),
        darkColorScheme = accentScheme(dark, darkSurfaces, isDark = true),
    )
}

/**
 * Everything but the accent: the page, the cards and bars on it (lowest to highest), the text on
 * them, separators, and the fill of chips, tiles and tracks ([fill]).
 */
data class Surfaces(
    val page: Color,
    val containerLow: Color,
    val container: Color,
    val containerHigh: Color,
    val containerHighest: Color,
    val text: Color,
    val secondaryText: Color,
    val outline: Color,
    val outlineVariant: Color,
    val fill: Color,
    val onFill: Color,
) {
    companion object {
        /** Greys close to iOS's system backgrounds, with a faint wash of [accent]. */
        fun neutralLight(accent: Color): Surfaces {
            fun tint(grey: Long, amount: Float) = lerp(Color(grey), accent, amount)
            return Surfaces(
                page = tint(0xFFFFFFFF, 0.03f),
                containerLow = tint(0xFFF7F7F9, 0.04f),
                container = tint(0xFFF2F2F7, 0.05f),
                containerHigh = tint(0xFFEBEBF0, 0.05f),
                containerHighest = tint(0xFFE5E5EA, 0.05f),
                text = Color(0xFF1C1C1E),
                secondaryText = Color(0xFF636366),
                outline = Color(0xFFAEAEB2),
                outlineVariant = Color(0xFFD1D1D6),
                fill = Color(0xFFE5E5EA),
                onFill = Color(0xFF1C1C1E),
            )
        }

        fun neutralDark(accent: Color): Surfaces {
            fun tint(grey: Long, amount: Float) = lerp(Color(grey), accent, amount)
            return Surfaces(
                page = tint(0xFF000000, 0.04f),
                containerLow = tint(0xFF121214, 0.05f),
                container = tint(0xFF1C1C1E, 0.05f),
                containerHigh = tint(0xFF242426, 0.05f),
                containerHighest = tint(0xFF2C2C2E, 0.05f),
                text = Color(0xFFF2F2F7),
                secondaryText = Color(0xFF98989F),
                outline = Color(0xFF636366),
                outlineVariant = Color(0xFF3A3A3C),
                fill = Color(0xFF3A3A3C),
                onFill = Color(0xFFF2F2F7),
            )
        }
    }
}

/** The full scheme: accent roles from [accent], the rest from [s] (see [AppColorScheme]). */
internal fun accentScheme(accent: Color, s: Surfaces, isDark: Boolean): ColorScheme =
    if (!isDark) lightColorScheme(
        primary = accent,
        onPrimary = s.text,
        // Containers are the surfaces' fill with the accent (or text) on them, not pastel accents.
        primaryContainer = s.fill,
        // A pastel accent is too faint on the fill: icons on it get a deeper shade of it.
        onPrimaryContainer = lerp(accent, s.text, 0.45f),
        inversePrimary = accent,
        secondary = s.text,
        onSecondary = s.page,
        secondaryContainer = s.fill,
        onSecondaryContainer = s.onFill,
        tertiary = s.secondaryText,
        onTertiary = s.page,
        tertiaryContainer = s.fill,
        onTertiaryContainer = s.onFill,
        error = Color(0xFFFF3B30),
        onError = Color.White,
        errorContainer = Color(0xFFFFE5E3),
        onErrorContainer = Color(0xFF8A1C14),
        background = s.page,
        onBackground = s.text,
        surface = s.page,
        onSurface = s.text,
        surfaceVariant = s.containerHighest,
        onSurfaceVariant = s.secondaryText,
        surfaceTint = Color.Transparent,
        inverseSurface = s.text,
        inverseOnSurface = s.page,
        outline = s.outline,
        outlineVariant = s.outlineVariant,
        scrim = Color.Black,
        surfaceBright = s.page,
        surfaceDim = s.containerHighest,
        surfaceContainerLowest = lerp(s.page, Color.White, 0.3f),
        surfaceContainerLow = s.containerLow,
        surfaceContainer = s.container,
        surfaceContainerHigh = s.containerHigh,
        surfaceContainerHighest = s.containerHighest,
    ) else darkColorScheme(
        primary = accent,
        onPrimary = s.page,
        primaryContainer = s.fill,
        onPrimaryContainer = accent,
        inversePrimary = accent,
        secondary = s.text,
        onSecondary = s.page,
        secondaryContainer = s.fill,
        onSecondaryContainer = s.onFill,
        tertiary = s.secondaryText,
        onTertiary = s.page,
        tertiaryContainer = s.containerHighest,
        onTertiaryContainer = s.onFill,
        error = Color(0xFFFF453A),
        onError = Color.White,
        errorContainer = Color(0xFF4A1512),
        onErrorContainer = Color(0xFFFFDAD6),
        background = s.page,
        onBackground = s.text,
        surface = s.page,
        onSurface = s.text,
        surfaceVariant = s.containerHighest,
        onSurfaceVariant = s.secondaryText,
        surfaceTint = Color.Transparent,
        inverseSurface = s.text,
        inverseOnSurface = s.page,
        outline = s.outline,
        outlineVariant = s.outlineVariant,
        scrim = Color.Black,
        surfaceBright = s.containerHighest,
        surfaceDim = s.page,
        surfaceContainerLowest = lerp(s.page, Color.Black, 0.5f),
        surfaceContainerLow = s.containerLow,
        surfaceContainer = s.container,
        surfaceContainerHigh = s.containerHigh,
        surfaceContainerHighest = s.containerHighest,
    )
