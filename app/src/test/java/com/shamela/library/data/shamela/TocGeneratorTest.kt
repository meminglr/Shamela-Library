package com.shamela.library.data.shamela

import com.shamela.library.data.remote.shamela.ShamelaEpubBuilder
import com.shamela.library.data.remote.shamela.ShamelaBookMeta
import com.shamela.library.data.remote.shamela.ShamelaPage
import com.shamela.library.data.remote.shamela.TocEntry
import com.shamela.library.data.remote.shamela.TocGenerator
import com.shamela.library.data.remote.shamela.TocNesting
import com.shamela.library.data.remote.shamela.TocRetrofit
import com.shamela.library.data.remote.shamela.TocSourcePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class TocGeneratorTest {
    private fun page(i: Int, vararg lines: String, part: String = "") =
        TocSourcePage(i, i.toString(), part, lines.joinToString("") { "<p>$it</p>" })

    @Test fun detectsHeadingLinesOnly() {
        assertEquals(1 to "سورة البقرة", TocGenerator.heading("[سورة البقرة]"))
        assertEquals(2 to "باب الطهارة", TocGenerator.heading("باب الطهارة"))
        assertEquals(1 to "كتاب الصلاة", TocGenerator.heading("(الكتاب الصلاة)".replace("الكتاب", "كتاب")))
        assertNull(TocGenerator.heading("قوله: سورة البقرة مدنية"))
        assertNull(TocGenerator.heading("فصل " + "كلمة ".repeat(20)))
    }

    @Test fun buildsNestedTocFromHeadings() {
        val pages = (1..60).map { i ->
            when (i) {
                1 -> page(i, "سورة الفاتحة", "قوله: نص")
                10 -> page(i, "سورة البقرة")
                12 -> page(i, "باب أول")
                20 -> page(i, "سورة آل عمران")
                else -> page(i, "نص عادي")
            }
        }
        val toc = TocGenerator.generate(pages)
        assertEquals(listOf("سورة الفاتحة", "سورة البقرة", "سورة آل عمران"), toc.map { it.text })
        assertEquals("باب أول", toc[1].children.single().text)
        assertEquals(12, toc[1].children.single().page)
    }

    @Test fun fallsBackToVolumeRanges() {
        val pages = (1..60).map { page(it, "نص عادي", part = if (it <= 30) "1" else "2") }
        val toc = TocGenerator.generate(pages)
        assertEquals(listOf("الجزء 1", "الجزء 2"), toc.map { it.text })
        assertEquals(2, toc[0].children.size)
        assertEquals("صفحة 1 – 25", toc[0].children[0].text)
    }

    @Test fun dropsRunningHeaders() {
        val pages = (1..40).map { page(it, "كتاب الطهارة", "نص") }
        assertTrue(TocGenerator.generate(pages).none { it.text == "كتاب الطهارة" })
    }

    @Test fun retrofitsOldFlatEpub() {
        val pages = (1..40).map { i ->
            ShamelaPage(i, i.toString(), "", if (i % 10 == 1) "<p>سورة رقم$i</p>" else "<p>نص</p>")
        }
        val file = File.createTempFile("book", ".epub")
        val meta = ShamelaBookMeta(1, "<p>x</p>", emptyList(), emptyMap(), 40)
        ShamelaEpubBuilder.build(file, 1, "t", "a", meta, pages)
        // The builder already generates one for a book without an index.
        ZipFile(file).use { z ->
            val nav = z.getInputStream(z.getEntry("OEBPS/nav.xhtml")).reader().readText()
            assertTrue(nav.contains("سورة رقم1"))
            assertTrue(nav.contains("auto-toc"))
        }
        assertFalse(TocRetrofit.upgrade(file))

        // An old flat file is upgraded once.
        val flat = (1..40).joinToString("") { "<li><a href=\"text/page_%02d.xhtml\">صفحة $it</a></li>".format(it) }
        assertTrue(TocRetrofit.needsUpgrade("<ol><li><a href=\"info.xhtml\">بطاقة الكتاب</a></li>$flat</ol>"))
        file.delete()
    }

    @Test fun upgradesFlatEpubInPlace() {
        val pages = (1..40).map { i ->
            ShamelaPage(i, i.toString(), "", if (i % 10 == 1) "<p>سورة رقم$i</p>" else "<p>نص</p>")
        }
        val built = File.createTempFile("built", ".epub")
        ShamelaEpubBuilder.build(built, 1, "t", "a", ShamelaBookMeta(1, "<p>x</p>", emptyList(), emptyMap(), 40), pages)
        // Rewrite it the way older versions saved it: a flat list of pages, no marker.
        val old = File.createTempFile("old", ".epub")
        java.util.zip.ZipOutputStream(old.outputStream()).use { out ->
            ZipFile(built).use { z ->
                for (e in z.entries()) {
                    out.putNextEntry(java.util.zip.ZipEntry(e.name))
                    if (e.name == "OEBPS/nav.xhtml") {
                        val flat = (1..40).joinToString("") { "<li><a href=\"text/page_%02d.xhtml\">صفحة $it</a></li>".format(it) }
                        val nav = z.getInputStream(e).reader().readText()
                        val s0 = nav.indexOf("<ol>"); val e0 = nav.lastIndexOf("</ol>")
                        out.write((nav.substring(0, s0).replace("<!-- auto-toc:1 -->", "") + "<ol><li><a href=\"info.xhtml\">بطاقة الكتاب</a></li>$flat" + nav.substring(e0)).toByteArray())
                    } else z.getInputStream(e).copyTo(out)
                    out.closeEntry()
                }
            }
        }
        assertTrue(TocRetrofit.upgrade(old))
        ZipFile(old).use { z ->
            val nav = z.getInputStream(z.getEntry("OEBPS/nav.xhtml")).reader().readText()
            assertTrue(nav.contains("سورة رقم11"))
            assertFalse(nav.contains("صفحة 7<"))
            val ncx = z.getInputStream(z.getEntry("OEBPS/toc.ncx")).reader().readText()
            assertTrue(ncx.contains("سورة رقم21"))
            assertTrue(ncx.contains("بطاقة الكتاب"))
        }
        assertFalse(TocRetrofit.upgrade(old))
        built.delete(); old.delete()
    }

    @Test fun dropsNavPropertyFromOldEpub() {
        val pages = (1..40).map { ShamelaPage(it, it.toString(), "", if (it % 10 == 1) "<p>سورة رقم$it</p>" else "<p>نص</p>") }
        val built = File.createTempFile("built2", ".epub")
        ShamelaEpubBuilder.build(built, 1, "t", "a", ShamelaBookMeta(1, "<p>x</p>", emptyList(), emptyMap(), 40), pages)
        fun opf(f: File) = ZipFile(f).use { z -> z.getInputStream(z.getEntry("OEBPS/content.opf")).reader().readText() }
        assertFalse(opf(built).contains("properties=\"nav\""))
        // An EPUB from an older version declares the navigation document.
        val old = File.createTempFile("old2", ".epub")
        java.util.zip.ZipOutputStream(old.outputStream()).use { out ->
            ZipFile(built).use { z ->
                for (e in z.entries()) {
                    out.putNextEntry(java.util.zip.ZipEntry(e.name))
                    if (e.name == "OEBPS/content.opf") out.write(opf(built).replace("id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\"", "id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"").toByteArray())
                    else z.getInputStream(e).copyTo(out)
                    out.closeEntry()
                }
            }
        }
        assertTrue(opf(old).contains("properties=\"nav\""))
        assertTrue(TocRetrofit.upgrade(old))
        assertFalse(opf(old).contains("properties=\"nav\""))
        assertFalse(TocRetrofit.upgrade(old))
        built.delete(); old.delete()
    }

    @Test fun nestsFlatTitlesByStructureWords() {
        val flat = listOf("تقديم", "المقدمة", "كتاب الطهارة", "باب المياه", "فصل في الوضوء", "مسألة في الماء", "باب الآنية",
            "كتاب الصلاة", "(١) باب الأذان", "خاتمة").mapIndexed { i, t -> TocEntry(t, i + 1) }
        val toc = TocNesting.nest(flat)
        assertEquals(listOf("تقديم", "المقدمة", "كتاب الطهارة", "كتاب الصلاة", "خاتمة"), toc.map { it.text })
        val tahara = toc[2]
        assertEquals(listOf("باب المياه", "باب الآنية"), tahara.children.map { it.text })
        assertEquals(listOf("فصل في الوضوء"), tahara.children[0].children.map { it.text })
        assertEquals(listOf("مسألة في الماء"), tahara.children[0].children[0].children.map { it.text })
        assertEquals(listOf("(١) باب الأذان"), toc[3].children.map { it.text })
    }

    @Test fun leavesUnstructuredOrNestedListsAlone() {
        val plain = (1..8).map { TocEntry("الحديث $it", it) }
        assertEquals(plain, TocNesting.nest(plain))
        val nested = listOf(TocEntry("كتاب", 1, listOf(TocEntry("باب", 2))), TocEntry("كتاب٢", 3), TocEntry("باب", 4))
        assertEquals(nested, TocNesting.nest(nested))
    }

    @Test fun retrofitNestsFlatNcxOfOurEpub() {
        val pages = (1..40).map { ShamelaPage(it, it.toString(), "", "<p>نص</p>") }
        val toc = listOf("كتاب الأول" to 1, "باب أ" to 5, "باب ب" to 9, "كتاب ثان" to 20, "باب ج" to 30)
            .map { TocEntry(it.first, it.second) }
        val file = File.createTempFile("flat", ".epub")
        ShamelaEpubBuilder.build(file, 1, "t", "a", ShamelaBookMeta(1, "<p>x</p>", toc, emptyMap(), 40), pages)
        // The builder nests it already.
        ZipFile(file).use { z ->
            val ncx = z.getInputStream(z.getEntry("OEBPS/toc.ncx")).reader().readText()
            assertTrue(Regex("""<content[^>]*/>\s*<navPoint""").containsMatchIn(ncx))
        }
        assertFalse(TocRetrofit.upgrade(file))
        file.delete()
    }
}
