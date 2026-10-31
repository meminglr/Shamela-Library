package com.shamela.library.data.openiti

import com.shamela.library.data.remote.openiti.OpenItiBooksRepoImpl
import com.shamela.library.data.remote.openiti.OpenItiText
import com.shamela.library.data.remote.shamela.ShamelaBookMeta
import com.shamela.library.data.remote.shamela.ShamelaEpubBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Reading OpenITI mARkdown (shaped like the corpus' texts) and the EPUB built from it. */
class OpenItiTextTest {

    private val text = """
        ﻿######OpenITI#

        #META# الكتاب: الاغتراب في أحكام الكلاب
        #META# المحقق: عبد الله الطيار
        #META#Header#End#

        بسم الله الرحمن الرحيم
        # الحمد لله الكريم التواب، أحمده حمد
        ~~الأحباب.
        PageV01P079
        ### | AUTO ((باب في ذكر الله الكلب باسمه))
        # قد ذكر الله الكلب في غير موضع ms01 في كتابه، فقال
        ~~تعالى: @QB@{فمثله كمثل الكلب}@QE@ وقيل PageV01P080
        ~~بل الآية في بلعام.
        ### || فصل
        # قفا نبك من ذكرى حبيب ومنزل %~% بسقط اللوى بين الدخول فحومل
        PageV02P001
        # تمت.
    """.trimIndent()

    private val book = OpenItiText.parse(text.lineSequence())

    @Test
    fun `header fields are read`() {
        assertEquals("الاغتراب في أحكام الكلاب", book.header["الكتاب"])
        assertEquals("عبد الله الطيار", book.header["المحقق"])
    }

    @Test
    fun `page markers close pages, with their volume and number`() {
        assertEquals(listOf("79", "80", "1", "2"), book.pages.map { it.printed })
        assertEquals(listOf("1", "1", "2", "2"), book.pages.map { it.part })
        assertTrue(book.pages[0].html.contains("<p>بسم الله الرحمن الرحيم</p>"))
        assertTrue(book.pages[0].html.contains("حمد الأحباب."))
        assertTrue(book.pages[3].html.contains("تمت."))
    }

    @Test
    fun `a paragraph running over a page marker goes on on the next page`() {
        assertTrue(book.pages[1].html.contains("وقيل</p>"))
        assertTrue(book.pages[2].html.startsWith("<p>بل الآية في بلعام.</p>"))
    }

    @Test
    fun `milestones and tags are dropped, verses keep their hemistichs`() {
        assertFalse(book.pages.any { "ms01" in it.html || "@Q" in it.html })
        assertTrue(book.pages[1].html.contains("في غير موضع في كتابه"))
        assertTrue(book.pages[2].html.contains("حبيب ومنزل ... بسقط اللوى"))
    }

    @Test
    fun `verses written with single percent signs lose their marks and numbers`() {
        val diwan = "#META#Header#End#\n# % وقالوا لو تشاء سلوت عنها % % فقلت لهم فاني لا أشاء % 2\n"
        val html = OpenItiText.parse(diwan.lineSequence()).pages.single().html
        assertEquals("<p>وقالوا لو تشاء سلوت عنها ... فقلت لهم فاني لا أشاء</p>\n", html)
    }

    @Test
    fun `headings become a nested table of contents`() {
        assertTrue(book.pages[1].html.contains("<h2>باب في ذكر الله الكلب باسمه</h2>"))
        assertEquals(1, book.toc.size)
        assertEquals("باب في ذكر الله الكلب باسمه", book.toc[0].text)
        assertEquals(2, book.toc[0].page)
        assertEquals("فصل", book.toc[0].children.single().text)
        assertEquals(3, book.toc[0].children.single().page)
    }

    @Test
    fun `a text without page markers is cut into pages`() {
        val long = (1..40).joinToString("\n") { "# " + List(30) { "كلمة" }.joinToString(" ") }
        val pages = OpenItiText.parse(("#META#Header#End#\n$long").lineSequence()).pages
        assertTrue(pages.size in 3..5)
        assertEquals((1..pages.size).map { it.toString() }, pages.map { it.printed })
    }

    @Test
    fun `the built EPUB is well-formed XHTML`() {
        val out = File.createTempFile("openiti", ".epub")
        try {
            val meta = ShamelaBookMeta(0, "<p>بطاقة</p>", book.toc, emptyMap(), book.pages.size)
            ShamelaEpubBuilder.build(
                out, 0, "الاغتراب في أحكام الكلاب", "ابن المبرد", meta, book.pages,
                identifier = "urn:openiti:0909IbnMabrid.Ightirab", source = "https://example.org", publisher = "OpenITI",
            )
            ZipFile(out).use { zip ->
                val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                zip.entries().toList().filter { it.name.endsWith("html") || it.name.endsWith(".opf") || it.name.endsWith(".ncx") }
                    .forEach { parser.parse(zip.getInputStream(it)) }
                val opf = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).reader().readText()
                assertTrue(opf.contains("urn:openiti:0909IbnMabrid.Ightirab"))
                val footer = zip.getInputStream(zip.getEntry("OEBPS/text/page_3.xhtml")).reader().readText()
                assertTrue(footer.contains("الجزء: 2 ¦ الصفحة: 1"))
            }
        } finally {
            out.delete()
        }
    }

    @Test
    fun `download links carry the version URI`() {
        val id = "0909IbnMabrid.Ightirab.ShamAY0034288-ara1"
        assertEquals(id, OpenItiBooksRepoImpl.bookIdOf("openiti://book/$id"))
        assertEquals(null, OpenItiBooksRepoImpl.bookIdOf("shamela://book/12"))
    }
}
