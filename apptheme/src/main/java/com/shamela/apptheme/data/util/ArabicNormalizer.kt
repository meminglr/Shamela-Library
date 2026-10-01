package com.shamela.apptheme.data.util


class ArabicNormalizer {

    companion object {
        /**
         * Bump this whenever the normalization rules change: already-indexed book pages must be
         * re-normalized (see DatabaseHelper.onUpgrade), otherwise queries and content diverge.
         */
        const val VERSION = 2

        // Define mapping for normalization
        private val normalizationMap = mapOf(
            'آ' to 'ا', // ALEF_MADDA to ALEF
            'أ' to 'ا', // ALEF_HAMZA_ABOVE to ALEF
            'إ' to 'ا', // ALEF_HAMZA_BELOW to ALEF
            'ٱ' to 'ا', // ALEF_WASLA to ALEF
            'ة' to 'ه', // TEH_MARBUTA to HEH
            'ى' to 'ي', // ALEF_MAKSURA to YEH
            'ؤ' to 'و', // WAW_HAMZA to WAW
            'ئ' to 'ي', // YEH_HAMZA to YEH
        )

        /** Characters that are dropped entirely: harakat, Quranic annotation marks and tatweel. */
        private fun isIgnorable(char: Char): Boolean =
            char in 'ً'..'ٟ' ||  // FATHATAN .. WAVY_HAMZA_BELOW (harakat)
                char == 'ٰ' ||        // SUPERSCRIPT ALEF
                char in 'ؐ'..'ؚ' ||  // Quranic signs
                char in 'ۖ'..'ۭ' ||  // Quranic annotation marks
                char == 'ـ'           // TATWEEL
    }

    /**
     * Normalize an Arabic text input.
     *
     * @param input The input string containing Arabic text.
     * @return The normalized string.
     */
    fun normalize(input: String): String {
        val result = StringBuilder(input.length)
        for (char in input) {
            if (!isIgnorable(char)) {
                result.append(normalizationMap[char] ?: char)
            }
        }
        return result.toString()
    }
}
