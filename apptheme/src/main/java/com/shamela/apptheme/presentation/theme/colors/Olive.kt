package com.shamela.apptheme.presentation.theme.colors

import androidx.compose.ui.graphics.Color

/** Lavender (stored key kept from the old olive): gentle lilac paper and plum ink. */
object Olive : AppColorScheme(
    name = "أخضر زيتي",
    light = Color(0xFFA98BC4),
    dark = Color(0xFFC9AEDD),
    lightSurfaces = Surfaces(
        page = Color(0xFFF8F3FB),
        containerLow = Color(0xFFF2EAF7),
        container = Color(0xFFEBE0F3),
        containerHigh = Color(0xFFE3D5EE),
        containerHighest = Color(0xFFDACAE8),
        text = Color(0xFF26162E),
        secondaryText = Color(0xFF634F70),
        outline = Color(0xFFB39CC2),
        outlineVariant = Color(0xFFDCCBE7),
        fill = Color(0xFFE5D6F0),
        onFill = Color(0xFF26162E),
    ),
    darkSurfaces = Surfaces(
        page = Color(0xFF140B18),
        containerLow = Color(0xFF1B1021),
        container = Color(0xFF22152A),
        containerHigh = Color(0xFF2A1A34),
        containerHighest = Color(0xFF33203F),
        text = Color(0xFFF2E6F8),
        secondaryText = Color(0xFFB79CC6),
        outline = Color(0xFF6C5379),
        outlineVariant = Color(0xFF34233F),
        fill = Color(0xFF3D2A4A),
        onFill = Color(0xFFF2E6F8),
    ),
)
