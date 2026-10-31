package com.shamela.apptheme.presentation.theme.colors

import androidx.compose.ui.graphics.Color

/** Plain: off-white and soft greys with a slate accent, the most minimal look. */
object Neutral : AppColorScheme(
    name = "محايد",
    light = Color(0xFF8A8F98),
    dark = Color(0xFFB5BAC3),
    lightSurfaces = Surfaces(
        page = Color(0xFFFAFAFA),
        containerLow = Color(0xFFF5F5F5),
        container = Color(0xFFEFEFF0),
        containerHigh = Color(0xFFE8E8EA),
        containerHighest = Color(0xFFE1E1E4),
        text = Color(0xFF1D1D1F),
        secondaryText = Color(0xFF6E6E73),
        outline = Color(0xFFB0B0B5),
        outlineVariant = Color(0xFFD6D6DA),
        fill = Color(0xFFE4E4E7),
        onFill = Color(0xFF1D1D1F),
    ),
    darkSurfaces = Surfaces(
        page = Color(0xFF111112),
        containerLow = Color(0xFF161618),
        container = Color(0xFF1C1C1E),
        containerHigh = Color(0xFF232326),
        containerHighest = Color(0xFF2B2B2E),
        text = Color(0xFFF2F2F4),
        secondaryText = Color(0xFF9C9CA3),
        outline = Color(0xFF5E5E64),
        outlineVariant = Color(0xFF343438),
        fill = Color(0xFF3A3A3E),
        onFill = Color(0xFFF2F2F4),
    ),
)
