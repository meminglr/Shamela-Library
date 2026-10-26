package com.shamela.apptheme.data.util

/**
 * Arabic as it sounds to a Turkish speaker: letters such a reader can't tell apart in writing
 * become one letter, as "i" and "ı" would — ص/ث→س, ذ/ظ/ض→ز, ط→ت, ح/خ→ه, ق→ك, ع→ا — and a lone
 * hamza, often left out, becomes a space. Searching this form finds "صحيح" for "سهيه".
 *
 * Works on text already passed through [ArabicNormalizer]. One char in, one char out: a position
 * in the folded text is the same position in the input, so a match found in the folded text can be
 * shown in the original.
 */
object ArabicSoundKey {
    private val folds = mapOf(
        'ص' to 'س', 'ث' to 'س',
        'ذ' to 'ز', 'ظ' to 'ز', 'ض' to 'ز',
        'ط' to 'ت',
        'ح' to 'ه', 'خ' to 'ه',
        'ق' to 'ك',
        'ع' to 'ا',
        'ء' to ' ',
    )

    fun fold(normalized: String): String {
        val result = StringBuilder(normalized.length)
        for (char in normalized) result.append(folds[char] ?: char)
        return result.toString()
    }
}
