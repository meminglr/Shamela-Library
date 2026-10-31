package com.shamela.apptheme.presentation.theme.colors

import android.content.Context


/**
 * The app has one palette, [Brown] (old books: parchment and sepia); there is no color choice. A
 * color stored by an older version that offered several is ignored.
 */
object AppColors {

    @Suppress("UNUSED_PARAMETER")
    fun colorSchemeOf(color: String, context: Context): AppColorScheme = Brown

    fun getAvailableColorSchemes(): List<String> = listOf(Brown.name)

    /** Stored key of the palette in use, whatever an older version stored. */
    @Suppress("UNUSED_PARAMETER")
    fun currentKeyOf(color: String): String = Brown.name
}
