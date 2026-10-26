package com.shamela.library.data.catalog

import com.shamela.apptheme.data.util.ArabicNormalizer

/**
 * Finds the books the two catalogs share. They have no common id, so a book of Kütüphane 1 is
 * paired with a shamela.ws book by title and author:
 *
 * - same title (after Arabic normalization; a site title is also tried without its "(…)" / "[…]"
 *   subtitle), and authors sharing a name word — or one of them has no author;
 * - Kütüphane 1 cuts long titles at ~40 characters, so a title left unpaired is also tried as the
 *   start of a site title, with the same author check.
 *
 * A book pairs only when exactly one candidate passes: two editions of a book, or one title by two
 * authors, stay separate rows rather than hiding a book behind the wrong one.
 */
object CatalogMatcher {
    private val normalizer = ArabicNormalizer()
    private val nonWord = Regex("""[^\p{L}\p{N} ]""")
    private val spaces = Regex("""\s+""")

    /** Name words too common to tell two authors apart. */
    private val commonNameWords = setOf("بن", "ابن", "ابو", "ابي", "بنت", "عبد", "الدين", "محمد", "احمد")

    /** Shorter cut titles are too generic to pair by their start. */
    private const val MIN_PREFIX_LENGTH = 8

    fun key(title: String): String =
        normalizer.normalize(title).replace(nonWord, " ").replace(spaces, " ").trim()

    /** The title before a "(…)", "[…]", "«" or ":" subtitle. */
    private fun shortKey(title: String): String = key(title.split('(', '[', '«', ':').first())

    fun authorWords(author: String): Set<String> =
        key(author).split(' ').mapNotNullTo(HashSet()) { word ->
            val bare = if (word.startsWith("ال") && word.length > 3) word.substring(2) else word
            bare.takeIf { it.length > 1 && it !in commonNameWords }
        }

    private fun sameAuthor(a: Set<String>, b: Set<String>) = a.isEmpty() || b.isEmpty() || a.any { it in b }

    /** Each library book that has exactly one counterpart on the site, mapped to it. */
    fun <L, S> match(
        library: List<L>,
        site: List<S>,
        libraryTitle: (L) -> String,
        libraryAuthor: (L) -> String,
        siteTitle: (S) -> String,
        siteAuthor: (S) -> String,
    ): Map<L, S> {
        val siteAuthors = HashMap<S, Set<String>>(site.size * 2)
        val byTitle = HashMap<String, MutableList<S>>(site.size * 2)
        val sortedKeys = ArrayList<Pair<String, S>>(site.size)
        for (book in site) {
            siteAuthors[book] = authorWords(siteAuthor(book))
            val full = key(siteTitle(book))
            byTitle.getOrPut(full) { mutableListOf() }.add(book)
            shortKey(siteTitle(book)).takeIf { it.isNotEmpty() && it != full }
                ?.let { byTitle.getOrPut(it) { mutableListOf() }.add(book) }
            sortedKeys += full to book
        }
        sortedKeys.sortBy { it.first }

        val result = HashMap<L, S>()
        for (book in library) {
            val title = key(libraryTitle(book))
            if (title.isEmpty()) continue
            val author = authorWords(libraryAuthor(book))
            val exact = (byTitle[title].orEmpty() + byTitle[shortKey(libraryTitle(book))].orEmpty()).distinct()
            val candidates = exact.ifEmpty {
                if (title.length < MIN_PREFIX_LENGTH) emptyList() else startingWith("$title ", sortedKeys)
            }.filter { sameAuthor(author, siteAuthors.getValue(it)) }
            candidates.singleOrNull()?.let { result[book] = it }
        }
        return result
    }

    /** Site books whose title starts with [prefix], from the title-sorted list. */
    private fun <S> startingWith(prefix: String, sorted: List<Pair<String, S>>): List<S> {
        var low = 0
        var high = sorted.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (sorted[mid].first < prefix) low = mid + 1 else high = mid
        }
        val found = mutableListOf<S>()
        while (low < sorted.size && sorted[low].first.startsWith(prefix)) found += sorted[low++].second
        return found
    }
}
