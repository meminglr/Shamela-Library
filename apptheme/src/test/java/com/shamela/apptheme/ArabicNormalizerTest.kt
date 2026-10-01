package com.shamela.apptheme

import com.shamela.apptheme.data.db.FtsQuery
import com.shamela.apptheme.data.util.ArabicNormalizer
import junit.framework.TestCase.assertEquals
import org.junit.Test

class ArabicNormalizerTest {
    private val normalizer = ArabicNormalizer()

    @Test
    fun testNormalizer() {
        val text = "فَرَاشَةٌ مُلَوَّنَةٌ تَطِيْرُ في البُسْتَانِ"
        val result = normalizer.normalize(text)
        assertEquals("فراشه ملونه تطير في البستان", result)
    }

    @Test
    fun hamzaForms_areUnified() {
        assertEquals(normalizer.normalize("احكام"), normalizer.normalize("أحكام"))
        assertEquals(normalizer.normalize("اسلام"), normalizer.normalize("إسلام"))
        assertEquals(normalizer.normalize("القران"), normalizer.normalize("القرآن"))
        assertEquals(normalizer.normalize("مسوول"), normalizer.normalize("مسؤول"))
    }

    @Test
    fun alefMaksura_tatweel_andSuperscriptAlef_areNormalized() {
        assertEquals(normalizer.normalize("علي"), normalizer.normalize("على"))
        assertEquals(normalizer.normalize("الله"), normalizer.normalize("اللـــه"))
        assertEquals(normalizer.normalize("رحمن"), normalizer.normalize("رحمٰن"))
    }

    @Test
    fun normalize_isIdempotent() {
        val once = normalizer.normalize("الصَّلَاةُ عَلَى النَّبِيِّ ﷺ")
        assertEquals(once, normalizer.normalize(once))
    }

    @Test
    fun ftsPhrase_escapesDoubleQuotes() {
        assertEquals("\"قال \"\"نعم\"\"\"", FtsQuery.phrase("قال \"نعم\""))
    }

    @Test
    fun ftsPhrase_normalizesAndTrims() {
        assertEquals("\"احكام الصلاه\"", FtsQuery.phrase("  أحكام الصلاة "))
    }
}
