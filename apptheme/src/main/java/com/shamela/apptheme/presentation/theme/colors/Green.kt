package com.shamela.apptheme.presentation.theme.colors

import androidx.compose.ui.graphics.Color

/** Sage: soft green paper and forest ink, easy on the eyes; at night, a deep forest. */
object Green : AppColorScheme(
    name = "أخضر",
    light = Color(0xFF7FA884),
    dark = Color(0xFFA5C9A9),
    lightSurfaces = Surfaces(
        page = Color(0xFFF1F7EE),
        containerLow = Color(0xFFE8F2E3),
        container = Color(0xFFDFECD8),
        containerHigh = Color(0xFFD4E5CC),
        containerHighest = Color(0xFFC9DDC0),
        text = Color(0xFF16261A),
        secondaryText = Color(0xFF4B6350),
        outline = Color(0xFF9DB59F),
        outlineVariant = Color(0xFFC8DBC6),
        fill = Color(0xFFD3E5CD),
        onFill = Color(0xFF16261A),
    ),
    darkSurfaces = Surfaces(
        page = Color(0xFF0A140C),
        containerLow = Color(0xFF0E1A10),
        container = Color(0xFF132216),
        containerHigh = Color(0xFF192B1C),
        containerHighest = Color(0xFF1F3523),
        text = Color(0xFFE2F1E3),
        secondaryText = Color(0xFF94B198),
        outline = Color(0xFF4E6B53),
        outlineVariant = Color(0xFF22362A),
        fill = Color(0xFF27402D),
        onFill = Color(0xFFE2F1E3),
    ),
)
