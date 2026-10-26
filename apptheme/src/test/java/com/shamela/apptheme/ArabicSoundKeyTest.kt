package com.shamela.apptheme

import com.shamela.apptheme.data.db.FtsQuery
import com.shamela.apptheme.data.util.ArabicNormalizer
import com.shamela.apptheme.data.util.ArabicSoundKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArabicSoundKeyTest {
    private val normalizer = ArabicNormalizer()
    private fun sound(text: String) = ArabicSoundKey.fold(normalizer.normalize(text))

    @Test
    fun soundAlikeLetters_foldTogether() {
        assertEquals(sound("صحيح البخاري"), sound("سهيه البهاري"))
        assertEquals(sound("تذكرة"), sound("تزكره"))
        assertEquals(sound("القاموس"), sound("الكاموس"))
        assertEquals(sound("الطبري"), sound("التبري"))
        assertEquals(sound("العمدة"), sound("الامده"))
    }

    @Test
    fun persianKeyboardLetters_areArabic() {
        assertEquals(normalizer.normalize("صحيح البخاري"), normalizer.normalize("صحیح البخاری"))
        assertEquals(normalizer.normalize("كتاب"), normalizer.normalize("کتاب"))
    }

    @Test
    fun folding_keepsPositions() {
        val text = normalizer.normalize("قال رسول الله صلى الله عليه وسلم في صحيح البخاري")
        assertEquals(text.length, ArabicSoundKey.fold(text).length)
    }

    @Test
    fun ftsPhrase_isFolded() {
        assertEquals(FtsQuery.phrase("صحيح"), FtsQuery.phrase("سهيه"))
    }

    @Test
    fun locate_findsExactSpellingFirst_thenSoundAlike_andReturnsThePageText() {
        val content = normalizer.normalize("روى البخاري في صحيحه وفي صحيح مسلم")
        assertEquals("صحيح", content.substring(FtsQuery.locate(content, "صحيح")!!))
        assertEquals("صحيح", content.substring(FtsQuery.locate(content, "سهيه")!!))
        assertEquals("البخاري", content.substring(FtsQuery.locate(content, "البهاري")!!))
        assertNull(FtsQuery.locate(content, "الترمذي"))
    }
}
