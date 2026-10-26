package com.shamela.library.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cases taken from the real catalogs (Kütüphane 1 vs shamela.ws). */
class CatalogMatcherTest {
    private data class Entry(val title: String, val author: String)

    private fun match(library: List<Entry>, site: List<Entry>) =
        CatalogMatcher.match(library, site, { it.title }, { it.author }, { it.title }, { it.author })

    @Test
    fun sameTitleWithHamzaAndAuthorOrderDifferences_pairs() {
        val library = Entry("أضواء البيان في إيضاح القرآن بالقرآن", "الشنقيطي، محمد الأمين")
        val site = Entry("اضواء البيان في ايضاح القران بالقران", "محمد الأمين الشنقيطي")
        assertEquals(site, match(listOf(library), listOf(site))[library])
    }

    @Test
    fun titleCutAtFortyCharacters_pairsWithTheFullTitle() {
        val library = Entry("تقويم طرق تعليم القرآن الكريم في مراحل", "فهد الرومي")
        val site = Entry("تقويم طرق تعليم القرآن الكريم في مراحل التعليم العام والتعليم الجامعي", "فهد الرومي")
        assertEquals(site, match(listOf(library), listOf(site))[library])
    }

    @Test
    fun sameTitleByAnotherAuthor_staysSeparate() {
        val library = Entry("المسند الجامع", "محمود محمد خليل")
        val site = Entry("المسند الجامع", "مجموعة من المؤلفين")
        assertTrue(match(listOf(library), listOf(site)).isEmpty())
    }

    @Test
    fun twoEditionsOnTheSite_stayAmbiguous() {
        val library = Entry("التخويف من النار والتعريف بحال دار البوار", "ابن رجب الحنبلي")
        val site = listOf(
            Entry("التخويف من النار والتعريف بحال دار البوار (151147)", "ابن رجب الحنبلي"),
            Entry("التخويف من النار والتعريف بحال دار البوار (4033)", "ابن رجب الحنبلي"),
        )
        assertTrue(match(listOf(library), site).isEmpty())
    }

    @Test
    fun cutTitleStartingTwoSiteTitles_staysAmbiguous() {
        val library = Entry("شرح مقدمة التفسير", "صالح آل الشيخ")
        val site = listOf(
            Entry("شرح مقدمة التفسير لابن تيمية", "صالح آل الشيخ"),
            Entry("شرح مقدمة التفسير لصالح آل الشيخ", "صالح آل الشيخ"),
        )
        assertTrue(match(listOf(library), site).isEmpty())
    }

    @Test
    fun shortTitlesArentPairedByTheirStart() {
        val library = Entry("الأم", "الشافعي")
        val site = Entry("الأم والطفل", "الشافعي")
        assertTrue(match(listOf(library), listOf(site)).isEmpty())
    }
}
