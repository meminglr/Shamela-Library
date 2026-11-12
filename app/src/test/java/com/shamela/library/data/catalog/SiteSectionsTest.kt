package com.shamela.library.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** shamela.ws section folders are shown under Kütüphane 1's section for the same subject. */
class SiteSectionsTest {
    @Test
    fun siteFolderIsShownUnderTheLibrarySection() {
        assertEquals("التفاسير", SiteSections.mergedSectionOf("التفسير"))
        assertEquals("فقه حنفي", SiteSections.mergedSectionOf("الفقه الحنفي"))
    }

    @Test
    fun otherFoldersKeepTheirName() {
        assertEquals("التفاسير", SiteSections.mergedSectionOf("التفاسير"))
        assertEquals("الكتب المصورة", SiteSections.mergedSectionOf("الكتب المصورة"))
    }

    @Test
    fun aLibrarySectionTakesInTheSiteFoldersMergedIntoIt() {
        val folders = SiteSections.foldersOf("الأدب والبلاغة")
        assertEquals("الأدب والبلاغة", folders.first())
        assertTrue(folders.containsAll(listOf("الأدب", "البلاغة", "العروض والقوافي", "الشعر ودواوينه")))
        assertEquals(listOf("الكتب المصورة", "الكتب المصورة - مرقم آليا"), SiteSections.foldersOf("الكتب المصورة"))
    }
}
