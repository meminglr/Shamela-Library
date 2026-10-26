package com.shamela.library.data.remote.shamela

import com.shamela.library.domain.model.BookInfoItem
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

data class ShamelaCategory(val id: Int, val name: String)

/** One book of the shamela.ws catalog, as listed on its category page. */
data class ShamelaBookEntry(
    val id: Int,
    val title: String,
    val author: String,
    val authorId: Int?,
    val category: String,
    val pageCount: Int,
    val authorDeathYear: Int?,
    /** The book card ("الكتاب: …", "المؤلف: …", …); bracketed notes have an empty label. */
    val about: List<BookInfoItem>,
)

/** Reads the category list (home page) and the books of a category page of shamela.ws. */
object ShamelaCatalogParser {

    private val categoryPath = Regex("""/category/(\d+)""")
    private val bookPath = Regex("""/book/(\d+)""")
    private val authorPath = Regex("""/author/(\d+)""")
    private val leadingNumber = Regex("""^\s*\d+\.\s*""")

    fun parseCategories(html: String): List<ShamelaCategory> =
        Jsoup.parse(html).select("a.cat_title[href]").mapNotNull { a ->
            val id = categoryPath.find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            // The name becomes a folder of the library: no path separators or other unsafe characters.
            val name = ShamelaCatalog.safeTitle(a.ownText().replace(leadingNumber, ""))
            if (name.isEmpty()) null else ShamelaCategory(id, name)
        }.distinctBy { it.id }

    fun parseCategoryBooks(html: String, categoryName: String): List<ShamelaBookEntry> =
        Jsoup.parse(html).select("div.book_item").mapNotNull { item ->
            val link = item.selectFirst("a.book_title[href]") ?: return@mapNotNull null
            val id = bookPath.find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val authorLink = item.selectFirst("a[href*=/author/]")
            val about = item.selectFirst("p.des")?.let(::cardLines).orEmpty()
            ShamelaBookEntry(
                id = id,
                title = link.text().trim(),
                author = authorLink?.text()?.trim().orEmpty(),
                authorId = authorLink?.let { authorPath.find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull() },
                category = categoryName,
                pageCount = pageCount(about),
                authorDeathYear = deathYear(about),
                about = about,
            )
        }

    /** The `<br>`-separated "label: value" lines of a book card. */
    internal fun cardLines(card: Element): List<BookInfoItem> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        for (node in card.childNodes()) {
            when {
                node is Element && node.normalName() == "br" -> {
                    lines += current.toString(); current.clear()
                }
                node is TextNode -> current.append(node.text())
                node is Element -> current.append(node.text())
            }
        }
        lines += current.toString()
        return lines.map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val colon = line.indexOf(':')
            if (colon in 1..40 && !line.startsWith("[")) {
                BookInfoItem(line.substring(0, colon).trim(), line.substring(colon + 1).trim())
            } else {
                BookInfoItem("", line)
            }
        }
    }

    private fun pageCount(about: List<BookInfoItem>): Int =
        about.firstOrNull { it.label == "عدد الصفحات" }?.value
            ?.let { Regex("""\d+""").find(westernDigits(it))?.value?.toIntOrNull() } ?: 0

    private val diedMarker = Regex("""ت\s*\.?\s*(\d{2,4})""")
    private val lifeSpan = Regex("""\d{1,4}\s*[-–]\s*(\d{2,4})\s*هـ""")

    /** Hijri death year from the author line: "(ت ٦٧٦هـ)", "[ت ١٤٤٦ هـ]" or "(٦٣١ - ٦٧٦ هـ)". */
    internal fun deathYear(about: List<BookInfoItem>): Int? {
        val line = about.firstOrNull { it.label == "المؤلف" }?.value?.let(::westernDigits) ?: return null
        return (diedMarker.find(line) ?: lifeSpan.find(line))?.groupValues?.get(1)?.toIntOrNull()
    }

    fun westernDigits(text: String): String = buildString(text.length) {
        for (c in text) append(
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                else -> c
            }
        )
    }
}
