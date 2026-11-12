package com.shamela.library.domain.util

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SectionTaxonomyTest {
    private val auto = SectionTaxonomy.AUTO_NUMBERED

    @Test
    fun allShafiiNamesGoToOneSection() {
        listOf("فقه شافعي", "فقه شافعي$auto", "الفقه الشافعي", "فقه شافعي - مرقم آليا", "فقه  شافعي ", "الفقه الشافعى", "فقه الشافعية")
            .forEach { assertEquals(it, "فقه شافعي", SectionTaxonomy.canonicalOf(it)) }
    }

    @Test
    fun autoNumberedEditionsFoldIntoTheirSection() {
        assertEquals("العقيدة", SectionTaxonomy.canonicalOf("العقيدة$auto"))
        assertEquals("علوم القرآن", SectionTaxonomy.canonicalOf("علوم القرآن - مرقم آليا"))
        assertEquals("علوم القرآن", SectionTaxonomy.canonicalOf("التجويد والقراءات - مرقم آليا"))
    }

    @Test
    fun siteSectionsMapToLibrarySections() {
        assertEquals("التفاسير", SectionTaxonomy.canonicalOf("التفسير"))
        assertEquals("فقه عام", SectionTaxonomy.canonicalOf("الفرائض والوصايا"))
        assertEquals("علوم أخرى", SectionTaxonomy.canonicalOf("الطب"))
    }

    @Test
    fun unknownAndAppSectionsKeepTheirName() {
        assertEquals("الكتب المصورة", SectionTaxonomy.canonicalOf("الكتب المصورة"))
        assertEquals("قسمي", SectionTaxonomy.canonicalOf("قسمي$auto"))
    }

    @Test
    fun canonicalIsStable() {
        SectionTaxonomy.aliasTable().forEach { (source, canonical) ->
            assertEquals(canonical, SectionTaxonomy.canonicalOf(source))
            assertEquals(canonical, SectionTaxonomy.canonicalOf(canonical))
            assertEquals(canonical, SectionTaxonomy.canonicalOf(canonical + auto))
        }
    }

    @Test
    fun sourcesOfListsEveryFolderOfASection() {
        val sources = SectionTaxonomy.sourcesOf("فقه شافعي")
        assertTrue("فقه شافعي" in sources)
        assertTrue("فقه شافعي$auto" in sources)
        assertTrue("الفقه الشافعي" in sources)
        sources.forEach { assertEquals("فقه شافعي", SectionTaxonomy.canonicalOf(it)) }
    }

    @Test
    fun everyCatalogSectionLandsInOneOfTheCanonicalSections() {
        val assets = File("src/main/assets")
        val library = File(assets, "categories").listFiles().orEmpty().map { it.name.removeSuffix(".json") }
        val openIti = JsonParser.parseReader(File(assets, "openiti/catalog.json").reader()).asJsonArray
            .map { it.asJsonObject["section"].asString }.toSet()
        assertTrue(library.isNotEmpty())
        val canonical = library.map { SectionTaxonomy.canonicalOf(it) }.toSet()
        // 72 library sections = 36 sections x 2 editions (+ some without an auto-numbered one).
        assertTrue("sections: ${canonical.size}", canonical.size < library.size)
        assertTrue(canonical.none { SectionTaxonomy.isAutoNumbered(it) })
        // Kütüphane 3's sections are already canonical ones.
        openIti.forEach { assertTrue("$it not canonical", SectionTaxonomy.canonicalOf(it) in canonical) }
        // The four madhhabs are one section each.
        listOf("فقه حنفي", "فقه مالكي", "فقه شافعي", "فقه حنبلي").forEach { assertTrue(it in canonical) }
    }

    @Test
    fun sectionGroupSeesCanonicalNames() {
        // Kept: grouping by the canonical name.
        assertEquals(SectionTaxonomy.canonicalOf("الفقه الشافعي$auto"), "فقه شافعي")
    }
}
