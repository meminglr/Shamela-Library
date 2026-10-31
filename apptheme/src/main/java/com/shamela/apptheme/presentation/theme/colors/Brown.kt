package com.shamela.apptheme.presentation.theme.colors

import androidx.compose.ui.graphics.Color

/**
 * Old books: the page is parchment and the cards a deeper paper, text in sepia ink, the accent a soft
 * worn-leather brown; the app's only palette. at night, a dark leather-bound library with warm cream text.
 */
object Brown : AppColorScheme(
    name = "بني",
    light = Color(0xFF8C6A4C),
    dark = Color(0xFFB8916C),
    lightSurfaces = Surfaces(
        page = Color(0xFFF3E9D2),
        containerLow = Color(0xFFEEE2C6),
        container = Color(0xFFE8DABB),
        containerHigh = Color(0xFFE1D1AE),
        containerHighest = Color(0xFFD9C7A1),
        text = Color(0xFF3A2A1A),
        secondaryText = Color(0xFF6F5A40),
        outline = Color(0xFFA8916C),
        outlineVariant = Color(0xFFCDB991),
        fill = Color(0xFFDCC9A2),
        onFill = Color(0xFF3A2A1A),
    ),
    darkSurfaces = Surfaces(
        page = Color(0xFF17120D),
        containerLow = Color(0xFF1F1811),
        container = Color(0xFF271E16),
        containerHigh = Color(0xFF30251B),
        containerHighest = Color(0xFF3A2D21),
        text = Color(0xFFEDDFC4),
        secondaryText = Color(0xFFB8A27F),
        outline = Color(0xFF7D6A50),
        outlineVariant = Color(0xFF4A3B2C),
        fill = Color(0xFF45362A),
        onFill = Color(0xFFEDDFC4),
    ),
)
