package com.shamela.apptheme.presentation.theme.colors

import androidx.compose.ui.graphics.Color

/** Dusty blue: a cool, airy paper and navy ink; at night, a night sky. */
object Blue : AppColorScheme(
    name = "أزرق",
    light = Color(0xFF6F94BF),
    dark = Color(0xFF9DBBDD),
    lightSurfaces = Surfaces(
        page = Color(0xFFF2F7FD),
        containerLow = Color(0xFFE8F0FA),
        container = Color(0xFFDEE9F7),
        containerHigh = Color(0xFFD4E2F3),
        containerHighest = Color(0xFFCAD9EE),
        text = Color(0xFF102033),
        secondaryText = Color(0xFF4A5B70),
        outline = Color(0xFF9FB1C7),
        outlineVariant = Color(0xFFC9D6E6),
        fill = Color(0xFFD6E3F4),
        onFill = Color(0xFF102033),
    ),
    darkSurfaces = Surfaces(
        page = Color(0xFF0A1220),
        containerLow = Color(0xFF0F1929),
        container = Color(0xFF142034),
        containerHigh = Color(0xFF1A2840),
        containerHighest = Color(0xFF213049),
        text = Color(0xFFE6EEF9),
        secondaryText = Color(0xFF93A5BD),
        outline = Color(0xFF4F617A),
        outlineVariant = Color(0xFF26344A),
        fill = Color(0xFF2A3A52),
        onFill = Color(0xFFE6EEF9),
    ),
)
