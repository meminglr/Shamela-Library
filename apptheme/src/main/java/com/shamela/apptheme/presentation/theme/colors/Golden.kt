package com.shamela.apptheme.presentation.theme.colors

import androidx.compose.ui.graphics.Color

/** Clay (stored key kept from the old golden): terracotta and warm earth. */
object Golden : AppColorScheme(
    name = "ذهبي",
    light = Color(0xFFC27C5E),
    dark = Color(0xFFE0A388),
    lightSurfaces = Surfaces(
        page = Color(0xFFFAF1EB),
        containerLow = Color(0xFFF5E8DF),
        container = Color(0xFFF0DFD3),
        containerHigh = Color(0xFFE9D3C5),
        containerHighest = Color(0xFFE2C7B7),
        text = Color(0xFF33221A),
        secondaryText = Color(0xFF7A5B4C),
        outline = Color(0xFFC4A08E),
        outlineVariant = Color(0xFFE6CDBF),
        fill = Color(0xFFEDD5C6),
        onFill = Color(0xFF33221A),
    ),
    darkSurfaces = Surfaces(
        page = Color(0xFF1A110D),
        containerLow = Color(0xFF211611),
        container = Color(0xFF291C16),
        containerHigh = Color(0xFF32231B),
        containerHighest = Color(0xFF3C2A21),
        text = Color(0xFFF7E6DC),
        secondaryText = Color(0xFFC4A190),
        outline = Color(0xFF7E5E50),
        outlineVariant = Color(0xFF3F2C23),
        fill = Color(0xFF4A3329),
        onFill = Color(0xFFF7E6DC),
    ),
)
