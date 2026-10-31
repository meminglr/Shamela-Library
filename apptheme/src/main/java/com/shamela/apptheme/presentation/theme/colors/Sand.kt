package com.shamela.apptheme.presentation.theme.colors

import androidx.compose.ui.graphics.Color

/** Sand: warm oat and sand, the calm everyday look. */
object Sand : AppColorScheme(
    name = "رملي",
    light = Color(0xFFB49A7A),
    dark = Color(0xFFD4BFA0),
    lightSurfaces = Surfaces(
        page = Color(0xFFF7F3EC),
        containerLow = Color(0xFFF2ECE2),
        container = Color(0xFFECE4D7),
        containerHigh = Color(0xFFE5DBCB),
        containerHighest = Color(0xFFDDD2C0),
        text = Color(0xFF2B2620),
        secondaryText = Color(0xFF6B6258),
        outline = Color(0xFFB3A898),
        outlineVariant = Color(0xFFD9CFC0),
        fill = Color(0xFFE3D9C9),
        onFill = Color(0xFF2B2620),
    ),
    darkSurfaces = Surfaces(
        page = Color(0xFF151310),
        containerLow = Color(0xFF1B1915),
        container = Color(0xFF22201B),
        containerHigh = Color(0xFF2A2722),
        containerHighest = Color(0xFF33302A),
        text = Color(0xFFEFE9DF),
        secondaryText = Color(0xFFADA393),
        outline = Color(0xFF6E665A),
        outlineVariant = Color(0xFF3A362F),
        fill = Color(0xFF403B33),
        onFill = Color(0xFFEFE9DF),
    ),
)
