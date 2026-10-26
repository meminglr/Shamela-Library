package com.shamela.apptheme.presentation.theme.colors

import android.content.Context


object AppColors {

    private const val MATERIAL = "ألوان النظام (اندرويد 12 فأعلى)"

    /**
     * Every palette, in the order the settings list them; the first is the default. Listed by hand:
     * `sealedSubclasses` (Kotlin reflection) came back empty in the release build, where R8 strips
     * the metadata it reads, and crashed the app at start. [MaterialYou] needs a Context, so it is
     * the null entry and is built on demand.
     */
    private val availableColorSchemes: List<AppColorScheme?> =
        listOf(Blue, Brown, Golden, Green, Indigo, null, MutedBlue, Olive)

    fun colorSchemeOf(color: String, context: Context): AppColorScheme {
        return availableColorSchemes.find { it?.name == color } ?: if (color == MATERIAL)
            MaterialYou(context)
        else
            availableColorSchemes.first()!!
    }

    fun getAvailableColorSchemes(): Set<String> {
        return availableColorSchemes.map { it?.name ?: MATERIAL }.toSet()
    }


}



