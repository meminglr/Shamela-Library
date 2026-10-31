package com.shamela.library.data.remote.shamela

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes an EPUB 3 (right-to-left) from a book read by [ShamelaBookScraper]. A Kotlin port of
 * `build_epub` of yshalsager/shamela-epub-exporter (GPL-3.0), with two differences: the title comes
 * from the catalog (the site's heading reads "كتاب …"), and footnotes are left as the site has them
 * (the exporter's footnote conversion drops footnotes it can't match).
 *
 * Reading order: the book card, then one file per page. `nav.xhtml` is in the manifest only, so the
 * reader's page count matches the book's.
 */
object ShamelaEpubBuilder {

    private val specialCharacters = mapOf(
        "﵀" to "رحمه الله", "﵏" to "رحمهم الله", "﷿" to "عز وجل", "﵊" to "عليه الصلاة والسلام",
        "﵄" to "رضي الله عنهما", "﵃" to "رضي الله عنهم", "﵅" to "رضي الله عنهن", "﵂" to "رضي الله عنها",
        "﵁" to "رضي الله عنه", "﷾" to "سبحانه وتعالى", "﵎" to "تبارك وتعالى", "﵇" to "عليه السلام",
        "﵍" to "عليها السلام", "﵈" to "عليهم السلام", "﵉" to "عليهما السلام", "﵌" to "صلى الله عليه وآله وسلم",
    )
    private val specialPattern = Regex(specialCharacters.keys.joinToString("|"))

    private const val CSS = """body{line-height:1.7;margin:1.5rem}
h1,h2,h3,h4,h5,h6{text-align:center}
.page-footer{text-align:center;font-size:smaller;margin-top:1em}
.hamesh{font-size:smaller}
"""

    fun build(
        output: File,
        bookId: Int,
        title: String,
        author: String,
        meta: ShamelaBookMeta,
        pages: List<ShamelaPage>,
        /** The book's unique id and where it comes from, for the EPUB metadata. */
        identifier: String = "urn:shamela:$bookId",
        source: String = "${ShamelaHttp.BASE}/book/$bookId",
        publisher: String = ShamelaHttp.BASE,
    ) {
        val sorted = pages.sortedBy { it.index }
        val width = sorted.lastOrNull()?.index?.toString()?.length ?: 1
        val multiPart = sorted.map { it.part }.filter { it.isNotEmpty() }.distinct().size > 1
        val depths = tocDepths(meta.toc)
        val fileOf = sorted.associate { it.index to "text/page_${it.index.toString().padStart(width, '0')}.xhtml" }

        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            zip.stored("mimetype", "application/epub+zip")
            zip.text("META-INF/container.xml", CONTAINER)
            zip.text("OEBPS/styles.css", CSS)
            zip.text("OEBPS/info.xhtml", xhtml("بطاقة الكتاب", "styles.css", toXhtmlBody(meta.aboutHtml)))

            sorted.forEach { page ->
                val chapters = meta.pageChapters[page.index].orEmpty()
                var body = toXhtmlBody(page.html) { doc -> headingsFor(doc, chapters, depths) }
                val footer = if (multiPart && page.part.isNotEmpty()) "الجزء: ${page.part} ¦ الصفحة: ${page.printed}"
                else "الصفحة: ${page.printed}"
                body += "\n<div class=\"page-footer\" style=\"text-align:center\">${escape(footer)}</div>"
                zip.text("OEBPS/${fileOf.getValue(page.index)}", xhtml(chapters.firstOrNull() ?: title, "../styles.css", body))
            }

            val toc = meta.toc.ifEmpty { sorted.map { TocEntry("صفحة ${it.printed}", it.index) } }
            zip.text("OEBPS/nav.xhtml", nav(title, toc, fileOf))
            zip.text("OEBPS/toc.ncx", ncx(identifier, title, toc, fileOf))
            zip.text("OEBPS/content.opf", opf(identifier, source, publisher, title, author, sorted.map { it.index }, fileOf))
        }
    }

    /** Turns `<p><span>[title]</span></p>` lines of this page's chapters into headings. */
    private fun headingsFor(doc: Document, chapters: List<String>, depths: Map<String, Int>) {
        if (chapters.isEmpty()) return
        doc.body().select("p").forEach { p ->
            val text = p.text().trim()
            if (!text.startsWith("[") || !text.endsWith("]")) return@forEach
            val chapter = text.removePrefix("[").removeSuffix("]").trim()
            if (chapter !in chapters) return@forEach
            val level = (depths[chapter] ?: 2).coerceIn(2, 6)
            p.tagName("h$level").text(chapter).clearAttributes()
        }
    }

    /** Parses site HTML and serializes it as well-formed XHTML (the reader's WebView is strict). */
    private fun toXhtmlBody(html: String, transform: (Document) -> Unit = {}): String {
        val doc = Jsoup.parseBodyFragment(specialPattern.replace(html) { specialCharacters.getValue(it.value) })
        doc.body().select("script, style, input, button, form").remove()
        doc.body().select("[class]").forEach { el ->
            if (el.hasClass("hamesh")) el.attr("class", "hamesh") else el.removeAttr("class")
        }
        doc.body().select("a[href^=https://shamela.ws], a[href^=/]").forEach { it.unwrap() }
        transform(doc)
        doc.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(Entities.EscapeMode.xhtml)
            .charset(Charsets.UTF_8)
            .prettyPrint(false)
        return doc.body().html()
    }

    private fun tocDepths(toc: List<TocEntry>, depth: Int = 2, into: MutableMap<String, Int> = mutableMapOf()): Map<String, Int> {
        toc.forEach { entry ->
            into.putIfAbsent(entry.text, depth.coerceAtMost(6))
            tocDepths(entry.children, depth + 1, into)
        }
        return into
    }

    private fun xhtml(title: String, css: String, body: String) = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="ar" xml:lang="ar" dir="rtl">
<head>
<meta charset="utf-8" />
<title>${escape(title)}</title>
<link rel="stylesheet" type="text/css" href="$css" />
</head>
<body dir="rtl">
$body
</body>
</html>
"""

    private fun nav(title: String, toc: List<TocEntry>, fileOf: Map<Int, String>): String {
        fun list(entries: List<TocEntry>): String {
            val items = entries.joinToString("") { entry ->
                val href = fileOf[entry.page]
                val children = list(entry.children)
                if (href == null) children.removePrefix("<ol>").removeSuffix("</ol>")
                else "<li><a href=\"$href\">${escape(entry.text)}</a>$children</li>"
            }
            return if (items.isEmpty()) "" else "<ol>$items</ol>"
        }
        val items = list(toc).removePrefix("<ol>").removeSuffix("</ol>")
        return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="ar" xml:lang="ar" dir="rtl">
<head>
<meta charset="utf-8" />
<title>${escape(title)}</title>
<link rel="stylesheet" type="text/css" href="styles.css" />
</head>
<body dir="rtl">
<nav epub:type="toc" id="toc">
<h1>فهرس الموضوعات</h1>
<ol><li><a href="info.xhtml">بطاقة الكتاب</a></li>$items</ol>
</nav>
</body>
</html>
"""
    }

    private fun ncx(identifier: String, title: String, toc: List<TocEntry>, fileOf: Map<Int, String>): String {
        var order = 1
        fun points(entries: List<TocEntry>): String = entries.joinToString("") { entry ->
            val href = fileOf[entry.page] ?: return@joinToString points(entry.children)
            val id = order++
            "<navPoint id=\"nav_$id\" playOrder=\"${id + 1}\"><navLabel><text>${escape(entry.text)}</text></navLabel>" +
                "<content src=\"$href\"/>${points(entry.children)}</navPoint>"
        }
        val body = points(toc)
        return """<?xml version="1.0" encoding="utf-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head>
<meta name="dtb:uid" content="${escape(identifier)}"/>
<meta name="dtb:depth" content="1"/>
<meta name="dtb:totalPageCount" content="0"/>
<meta name="dtb:maxPageNumber" content="0"/>
</head>
<docTitle><text>${escape(title)}</text></docTitle>
<navMap>
<navPoint id="info" playOrder="1"><navLabel><text>بطاقة الكتاب</text></navLabel><content src="info.xhtml"/></navPoint>
$body
</navMap>
</ncx>
"""
    }

    private fun opf(
        identifier: String,
        source: String,
        publisher: String,
        title: String,
        author: String,
        indexes: List<Int>,
        fileOf: Map<Int, String>,
    ): String {
        val modified = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val manifest = indexes.joinToString("\n") {
            "<item id=\"p$it\" href=\"${fileOf.getValue(it)}\" media-type=\"application/xhtml+xml\"/>"
        }
        val spine = indexes.joinToString("\n") { "<itemref idref=\"p$it\"/>" }
        return """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="3.0">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="bookid">${escape(identifier)}</dc:identifier>
<dc:title>${escape(title)}</dc:title>
<dc:creator>${escape(author.ifBlank { "-" })}</dc:creator>
<dc:source>${escape(source)}</dc:source>
<dc:publisher>${escape(publisher)}</dc:publisher>
<dc:language>ar</dc:language>
<meta property="dcterms:modified">$modified</meta>
</metadata>
<manifest>
<item id="info" href="info.xhtml" media-type="application/xhtml+xml"/>
<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
<item id="css" href="styles.css" media-type="text/css"/>
$manifest
</manifest>
<spine toc="ncx" page-progression-direction="rtl">
<itemref idref="info"/>
$spine
</spine>
</package>
"""
    }

    private const val CONTAINER = """<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles>
<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
</rootfiles>
</container>
"""

    internal fun escape(value: String) = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun ZipOutputStream.text(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    /** The `mimetype` entry must come first and be stored uncompressed. */
    private fun ZipOutputStream.stored(name: String, content: String) {
        val bytes = content.toByteArray(Charsets.US_ASCII)
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
        }
        putNextEntry(entry)
        write(bytes)
        closeEntry()
    }
}
