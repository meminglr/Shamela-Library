package com.shamela.library.data.shamela

import com.shamela.library.data.remote.shamela.ShamelaBookEntry
import com.shamela.library.data.remote.shamela.ShamelaBookScraper
import com.shamela.library.data.remote.shamela.ShamelaCatalog
import com.shamela.library.data.remote.shamela.ShamelaCatalogParser
import com.shamela.library.data.remote.shamela.ShamelaEpubBuilder
import com.shamela.library.data.remote.shamela.ShamelaHttp
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Parsing of saved shamela.ws pages (test/resources/shamela) and the EPUB built from them. */
class ShamelaParsingTest {

    private fun page(name: String): String =
        javaClass.classLoader!!.getResource("shamela/$name.html")!!.readText()

    private fun doc(name: String) = Jsoup.parse(page(name), ShamelaHttp.BASE)

    @Test
    fun `home page lists the 40 categories`() {
        val categories = ShamelaCatalogParser.parseCategories(page("home"))
        assertEquals(40, categories.size)
        assertEquals(1, categories.first().id)
        assertEquals("العقيدة", categories.first().name)
        assertEquals("الرقائق والآداب والأذكار", categories.first { it.id == 23 }.name)
    }

    @Test
    fun `category page gives id, title, author, page count and death year`() {
        val books = ShamelaCatalogParser.parseCategoryBooks(page("category_23"), "الرقائق والآداب والأذكار")
        assertTrue(books.size > 500)
        val nawawi = books.first { it.id == 12836 }
        assertEquals("الأربعون النووية", nawawi.title)
        assertEquals("النووي", nawawi.author)
        assertEquals(44, nawawi.authorId)
        assertEquals(115, nawawi.pageCount)
        assertEquals(676, nawawi.authorDeathYear)
        assertEquals("دار المنهاج للنشر والتوزيع، لبنان - بيروت", nawawi.about.first { it.label == "الناشر" }.value)
        assertTrue(nawawi.about.any { it.label.isEmpty() && it.value == "[ترقيم الكتاب موافق للمطبوع]" })
    }

    @Test
    fun `normal pages are not taken for the browser check`() {
        listOf("home", "category_23", "book_12836", "book_12836_13", "book_10391_10").forEach {
            assertFalse(it, ShamelaHttp.looksLikeChallenge(page(it)))
        }
        assertTrue(ShamelaHttp.looksLikeChallenge("<html><head><title>Just a moment...</title></head></html>"))
    }

    @Test
    fun `same titles in a category get their number`() {
        fun entry(id: Int, title: String) =
            ShamelaBookEntry(id, title, "", null, "c", 0, null, emptyList())
        val result = ShamelaCatalog.disambiguate(listOf(entry(1, "كتاب"), entry(2, "كتاب"), entry(3, "آخر: جزء/1")))
        assertEquals(listOf("كتاب (1)", "كتاب (2)", "آخر- جزء-1"), result.map { it.title })
    }

    @Test
    fun `book index page gives table of contents, chapters and page total`() {
        val meta = ShamelaBookScraper.parseMeta(12836, doc("book_12836"), doc("book_12836_1"))
        assertEquals(81, meta.totalPages)
        assertEquals("مقدمة المؤلف", meta.toc.first().text)
        assertEquals(1, meta.toc.first().page)
        assertTrue(meta.toc.size >= 42)
        assertEquals(listOf("الحديث الأول"), meta.pageChapters[12])
        assertTrue(meta.aboutHtml.contains("الناشر"))
        assertFalse(meta.aboutHtml.contains("betaka-index"))
    }

    @Test
    fun `book pages keep printed number, volume and footnotes`() {
        val page = ShamelaBookScraper.parsePage(10, doc("book_10391_10"))
        assertEquals("14", page.printed)
        assertTrue(page.html.contains("class=\"hamesh\""))
        assertTrue(page.html.contains("٥٧٣٦") && page.html.contains("٥٧٣٧"))
        assertFalse(page.html.contains("btn_tag"))

        val volumePage = ShamelaBookScraper.parsePage(1, doc("book_20882_1"))
        assertEquals("20", volumePage.part)
    }

    @Test
    fun `epub is well formed, titled from the catalog and keeps footnotes`() {
        val meta = ShamelaBookScraper.parseMeta(12836, doc("book_12836"), doc("book_12836_1"))
        val pages = listOf(
            ShamelaBookScraper.parsePage(1, doc("book_12836_1")),
            ShamelaBookScraper.parsePage(13, doc("book_12836_13")),
            // A footnoted page standing in as page 14.
            ShamelaBookScraper.parsePage(14, doc("book_10391_10")),
        )
        val out = File.createTempFile("shamela", ".epub").apply { deleteOnExit() }
        ShamelaEpubBuilder.build(out, 12836, "الأربعون النووية", "النووي", meta, pages)

        ZipFile(out).use { zip ->
            val first = zip.entries().nextElement()
            assertEquals("mimetype", first.name)
            assertEquals(ZipEntry.STORED, first.method)

            val xml = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            zip.entries().toList().filter { it.name.endsWith(".xhtml") || it.name.endsWith(".opf") || it.name.endsWith(".ncx") }
                .forEach { entry ->
                    // Throws on markup that isn't well-formed XML.
                    zip.getInputStream(entry).use { xml.newDocumentBuilder().parse(it) }
                }

            val opf = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).reader().readText()
            assertTrue(opf.contains("<dc:title>الأربعون النووية</dc:title>"))
            assertFalse(opf.contains("كتاب الأربعون"))
            assertFalse(opf.contains("idref=\"nav\""))
            assertEquals(4, Regex("<itemref ").findAll(opf).count()) // card + 3 pages

            val first1 = zip.getInputStream(zip.getEntry("OEBPS/text/page_01.xhtml")).reader().readText()
            assertTrue(first1.contains("<h2>مقدمة المؤلف</h2>"))
            assertTrue(first1.contains("الصفحة: 35"))

            val footnoted = zip.getInputStream(zip.getEntry("OEBPS/text/page_14.xhtml")).reader().readText()
            assertTrue(footnoted.contains("class=\"hamesh\""))
            assertTrue(footnoted.contains("٥٧٣٦") && footnoted.contains("٥٧٣٧"))
        }
    }
}
