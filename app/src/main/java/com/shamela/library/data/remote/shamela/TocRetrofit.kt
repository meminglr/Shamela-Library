package com.shamela.library.data.remote.shamela

import org.jsoup.Jsoup
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Repairs EPUBs this app made in earlier versions, without touching their pages:
 * - a book whose contents is the flat "صفحة N" list of every page (the old fallback for a book
 *   without an index) gets a generated one (`nav.xhtml`, `toc.ncx`);
 * - the OPF no longer declares the navigation document, so the reader shows the nested,
 *   collapsible `toc.ncx` instead of flattening it.
 */
object TocRetrofit {
    private val pageEntry = Regex("""OEBPS/(text/page_(\d+)\.xhtml)""")
    private val navItem = Regex("""<li><a href="[^"]*">([^<]*)</a></li>""")
    private val footer = Regex("""(?:الجزء:\s*(.+?)\s*¦\s*)?الصفحة:\s*(.+)""")

    /** True when [file] was upgraded. Safe to call on any file: anything unexpected leaves it alone. */
    fun upgrade(file: File): Boolean = runCatching { upgradeOrThrow(file) }.getOrDefault(false)

    private fun upgradeOrThrow(file: File): Boolean {
        ZipFile(file).use { zip ->
            val navEntry = zip.getEntry("OEBPS/nav.xhtml") ?: return false
            val opfEntry = zip.getEntry("OEBPS/content.opf") ?: return false
            val nav = zip.getInputStream(navEntry).bufferedReader(Charsets.UTF_8).readText()
            val opf = zip.getInputStream(opfEntry).bufferedReader(Charsets.UTF_8).readText()
            val replacements = HashMap<String, ByteArray>()

            // The reader flattens a navigation document, so the OPF must not declare one (see the builder).
            if (opf.contains(ShamelaEpubBuilder.NAV_PROPERTY) && zip.getEntry("OEBPS/toc.ncx") != null) {
                replacements["OEBPS/content.opf"] = opf.replace(ShamelaEpubBuilder.NAV_PROPERTY, "").toByteArray(Charsets.UTF_8)
            }

            val pageNames = HashMap<Int, String>()
            zip.entries().asSequence().forEach { e ->
                pageEntry.matchEntire(e.name)?.let { pageNames[it.groupValues[2].toInt()] = it.groupValues[1] }
            }
            // Only books this app built have the page files and the book card in their contents.
            val ours = pageNames.size >= 2 && nav.contains(ShamelaEpubBuilder.NAV_HEAD)

            if (ours && needsUpgrade(nav)) {
                val pages = pageNames.map { (index, name) ->
                    val html = zip.getInputStream(zip.getEntry("OEBPS/$name")).bufferedReader(Charsets.UTF_8).readText()
                    val body = Jsoup.parse(html).body()
                    val footerText = body.selectFirst(".page-footer")?.text().orEmpty()
                    val match = footer.find(footerText)
                    TocSourcePage(
                        index,
                        printed = match?.groupValues?.get(2)?.trim().orEmpty().ifEmpty { index.toString() },
                        part = match?.groupValues?.get(1)?.trim().orEmpty(),
                        html = body.html(),
                    )
                }
                val toc = TocGenerator.generate(pages)
                if (toc.isNotEmpty()) putToc(zip, replacements, nav, toc, pageNames)
            } else if (ours) {
                // A flat contents (shamela.ws gives no nesting) is nested by its titles.
                val ncxEntry = zip.getEntry("OEBPS/toc.ncx")
                val ncx = ncxEntry?.let { zip.getInputStream(it).bufferedReader(Charsets.UTF_8).readText() }
                if (ncx != null && !nestedNavPoint.containsMatchIn(ncx)) {
                    val flat = ncxEntries(ncx)
                    val nested = TocNesting.nest(flat)
                    if (nested != flat) putToc(zip, replacements, nav, nested, pageNames)
                }
            }
            if (replacements.isEmpty()) return false

            val tmp = File(file.path + ".toc")
            try {
                writeCopy(zip, tmp, replacements)
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
            zip.close()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
        return true
    }

    private val nestedNavPoint = Regex("""<content[^>]*/>\s*<navPoint""")
    private val ncxItem = Regex("""<navLabel><text>(.*?)</text></navLabel><content src="([^"]*)"/>""")
    private val pageIndex = Regex("""page_(\d+)\.xhtml""")

    /** The entries of a flat `toc.ncx`, without the book card. */
    private fun ncxEntries(ncx: String): List<TocEntry> = ncxItem.findAll(ncx).mapNotNull { m ->
        val page = pageIndex.find(m.groupValues[2])?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
        TocEntry(unescape(m.groupValues[1]), page)
    }.toList()

    private fun unescape(text: String) = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    /** Queues the rewritten `nav.xhtml` and `toc.ncx` for [toc]. */
    private fun putToc(zip: ZipFile, replacements: MutableMap<String, ByteArray>, nav: String, toc: List<TocEntry>, pageNames: Map<Int, String>) {
        replacements["OEBPS/nav.xhtml"] = rewriteNav(nav, toc, pageNames).toByteArray(Charsets.UTF_8)
        val ncx = zip.getEntry("OEBPS/toc.ncx")?.let { zip.getInputStream(it).bufferedReader(Charsets.UTF_8).readText() } ?: return
        val from = ncx.indexOf("</navPoint>") + "</navPoint>".length
        val to = ncx.lastIndexOf("</navMap>")
        if (from > 10 && to > from) {
            val points = ShamelaEpubBuilder.ncxPoints(toc, pageNames)
            replacements["OEBPS/toc.ncx"] = (ncx.substring(0, from) + "\n" + points + "\n" + ncx.substring(to)).toByteArray(Charsets.UTF_8)
        }
    }

    /** The nav lists nothing but the book card and one "صفحة N" entry per page, and wasn't generated. */
    internal fun needsUpgrade(nav: String): Boolean {
        if (nav.contains("auto-toc")) return false
        val labels = navItem.findAll(nav).map { it.groupValues[1] }.toList()
        val rest = labels.filter { it != "بطاقة الكتاب" }
        return rest.size >= 2 && rest.all { it.startsWith("صفحة ") && !it.contains('–') }
    }

    private fun rewriteNav(nav: String, toc: List<TocEntry>, pageNames: Map<Int, String>): String {
        val fileOf = pageNames.mapValues { "text/${it.value.removePrefix("text/")}" }
        val items = ShamelaEpubBuilder.navItems(toc, fileOf)
        val start = nav.indexOf("<ol>")
        val end = nav.lastIndexOf("</ol>")
        val body = nav.substring(0, start) + "<ol>${ShamelaEpubBuilder.NAV_HEAD}$items" + nav.substring(end)
        return if (body.contains(ShamelaEpubBuilder.AUTO_TOC_MARKER)) body
        else body.replace("<nav ", "${ShamelaEpubBuilder.AUTO_TOC_MARKER}\n<nav ")
    }

    private fun writeCopy(zip: ZipFile, out: File, replacements: Map<String, ByteArray>) {
        ZipOutputStream(out.outputStream().buffered()).use { dest ->
            // `mimetype` first and uncompressed, as an EPUB requires.
            zip.getEntry("mimetype")?.let { e ->
                val bytes = zip.getInputStream(e).readBytes()
                dest.putNextEntry(ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value
                })
                dest.write(bytes)
                dest.closeEntry()
            }
            for (e in zip.entries()) {
                if (e.name == "mimetype") continue
                dest.putNextEntry(ZipEntry(e.name))
                val replacement = replacements[e.name]
                if (replacement != null) dest.write(replacement) else zip.getInputStream(e).use { it.copyTo(dest) }
                dest.closeEntry()
            }
        }
    }
}
