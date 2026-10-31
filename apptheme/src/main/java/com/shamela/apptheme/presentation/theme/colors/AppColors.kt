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
        listOf(Brown, Sand, Neutral, Green, Blue, Golden, Olive, null)

    /** Palettes that were removed, and the one their users get now. */
    private val replaced = mapOf(
        "أزرق باهت" to Blue,
        "نيلي" to Blue,
    )

    fun colorSchemeOf(color: String, context: Context): AppColorScheme {
        return availableColorSchemes.find { it?.name == color } ?: replaced[color] ?: if (color == MATERIAL)
            MaterialYou(context)
        else
            availableColorSchemes.first()!!
    }

    /** The stored key of each palette, in the order the settings list them. */
    fun getAvailableColorSchemes(): List<String> = availableColorSchemes.map { it?.name ?: MATERIAL }

    /** Stored key of the palette a removed one became (or the key itself). */
    fun currentKeyOf(color: String): String = replaced[color]?.name ?: color
}
