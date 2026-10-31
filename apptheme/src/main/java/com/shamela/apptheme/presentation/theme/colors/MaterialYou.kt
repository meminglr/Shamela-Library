package com.shamela.apptheme.presentation.theme.colors

import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

/** The system's (wallpaper) accent, on the same grey surfaces as most palettes. */
class MaterialYou(context: Context) : AppColorScheme(
    name = "ألوان النظام (اندرويد 12 فأعلى)",
    light = dynamicLightColorScheme(context).primary,
    dark = dynamicDarkColorScheme(context).primary,
)
