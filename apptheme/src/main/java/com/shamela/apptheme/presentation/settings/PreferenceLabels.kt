package com.shamela.apptheme.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shamela.apptheme.R

/**
 * Theme, color scheme and font names are stored in SharedPreferences under their original
 * (Arabic) names, so those names must stay stable. This maps each stored key to a localized
 * display name; unknown keys are shown as-is.
 */
object PreferenceLabels {
    private val labels: Map<String, Int> = mapOf(
        "تلقائي" to R.string.theme_auto,
        "فاتح" to R.string.theme_light,
        "مظلم" to R.string.theme_dark,
        "ذهبي" to R.string.color_golden,
        "أزرق" to R.string.color_blue,
        "بني" to R.string.color_brown,
        "أخضر" to R.string.color_green,
        "نيلي" to R.string.color_indigo,
        "أزرق باهت" to R.string.color_muted_blue,
        "أخضر زيتي" to R.string.color_olive,
        "ألوان النظام (اندرويد 12 فأعلى)" to R.string.color_material_you,
        "خط النظام" to R.string.font_system,
        "خط أميري" to R.string.font_amiri,
        "خط كِتاب" to R.string.font_kitab,
        "خط تَجَوَّل" to R.string.font_tajawal,
        "خط كوفي" to R.string.font_kufi,
        "خط المسيري" to R.string.font_messiri,
        "خط كايرو" to R.string.font_cairo,
        "خط آي بي إم" to R.string.font_ibm_plex,
        "خط نوتو نسخ" to R.string.font_noto_naskh,
        "خط شهرزاد" to R.string.font_scheherazade,
    )

    @StringRes
    fun labelRes(key: String): Int? = labels[key]

    @Composable
    fun label(key: String): String = labelRes(key)?.let { stringResource(it) } ?: key
}
