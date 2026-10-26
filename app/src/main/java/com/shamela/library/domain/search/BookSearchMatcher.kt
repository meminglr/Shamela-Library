package com.shamela.library.domain.search

import com.shamela.apptheme.data.util.ArabicNormalizer
import com.shamela.apptheme.data.util.ArabicSoundKey
import com.shamela.library.domain.model.Book
import java.util.concurrent.ConcurrentHashMap

/**
 * Book-name search, forgiving of how someone who doesn't know Arabic well writes it:
 *
 * - harakat, hamza forms, tâ marbûta and alif maqsûra don't matter (normalized away);
 * - the Persian/Turkish keyboard's ی and ک are the Arabic ي and ك;
 * - letters that sound alike to a Turkish speaker count as the same, as "i" and "ı" would
 *   ([ArabicSoundKey]): ص/ث→س, ذ/ظ/ض→ز, ط→ت, ح/خ→ه, ق→ك, ع→ا;
 * - small typos are tolerated, more in longer words;
 * - when nothing matches, the closest titles are offered ([search], [isClosest]).
 *
 * Exact spelling ranks above sound-alike spelling, which ranks above typos; within a tier the
 * title closest in length to the query comes first.
 */
object BookSearchMatcher {
    private val normalizer = ArabicNormalizer()

    // Score tiers (see [relevanceScore]); each is multiplied by [TIER] and refined by closeness.
    private const val EXACT = 60
    private const val PREFIX = 50
    private const val CONTAINS = 40
    private const val ALL_WORDS = 30
    /** The same tiers on the sound-alike spelling sit just below the exact-spelling ones. */
    private const val SOUND_ALIKE_PENALTY = 2
    private const val TYPOS = 20
    private const val AUTHOR = 10
    private const val CLOSEST = 1
    private const val TIER = 1000

    /** Below this score a result is only "closest", not a match. */
    private const val MATCH_THRESHOLD = AUTHOR * TIER - TIER / 2

    /** How many closest titles to offer when nothing matches. */
    private const val CLOSEST_LIMIT = 30

    private val nonWord = Regex("""[^\p{L}\p{N} ]""")
    private val spaces = Regex("""\s+""")

    /** One char of text in its spelling key (harakat dropped, letter forms unified). */
    private fun spellingOf(char: Char): String = normalizer.normalize(char.toString())

    /** [spellingOf], then with sound-alike letters merged ([ArabicSoundKey]). */
    private fun soundOf(char: Char): String = ArabicSoundKey.fold(spellingOf(char))

    private fun clean(text: String) = text.replace(nonWord, " ").replace(spaces, " ").trim()

    fun spellingKey(text: String): String = clean(normalizer.normalize(text))
    fun soundKey(text: String): String = clean(ArabicSoundKey.fold(normalizer.normalize(text)))

    /** Keys of a title or author, computed once: a catalog is searched over and over. */
    private class Keys(val spelling: String, val sound: String) {
        val spellingWords = spelling.split(' ').filter { it.isNotEmpty() }
        val soundWords = sound.split(' ').filter { it.isNotEmpty() }
    }

    private val keysCache = ConcurrentHashMap<String, Keys>()
    private fun keys(text: String): Keys {
        keysCache[text]?.let { return it }
        if (keysCache.size > 50_000) keysCache.clear()
        return Keys(spellingKey(text), soundKey(text)).also { keysCache[text] = it }
    }

    fun matches(book: Book, query: String): Boolean {
        if (query.isEmpty()) return true
        return relevanceScore(book, query) >= MATCH_THRESHOLD
    }

    /**
     * Higher is more relevant; 0 is no match. Callers sort descending. Tiers, best first: the
     * title equals / starts with / contains the query / has all its words, in exact spelling, then
     * the same in sound-alike spelling, then all words with small typos, then the author.
     */
    fun relevanceScore(book: Book, query: String): Int {
        if (query.isBlank()) return 0
        val q = keys(query)
        if (q.sound.isEmpty()) return 0
        val title = keys(book.title)
        val tier = tierOf(q, title) ?: tierOf(q, keys(book.author))?.let { AUTHOR } ?: return 0
        // Within a tier: the title nearest the query's length (an exact title before a longer one).
        val lengthGap = kotlin.math.abs(title.spelling.length - q.spelling.length).coerceAtMost(TIER / 2 - 1)
        return tier * TIER + (TIER / 2 - lengthGap)
    }

    private fun tierOf(q: Keys, text: Keys): Int? = when {
        text.spelling == q.spelling -> EXACT
        text.spelling.startsWith(q.spelling) -> PREFIX
        text.spelling.contains(q.spelling) -> CONTAINS
        q.spellingWords.all { text.spelling.contains(it) } -> ALL_WORDS
        text.sound == q.sound -> EXACT - SOUND_ALIKE_PENALTY
        text.sound.startsWith(q.sound) -> PREFIX - SOUND_ALIKE_PENALTY
        text.sound.contains(q.sound) -> CONTAINS - SOUND_ALIKE_PENALTY
        q.soundWords.all { text.sound.contains(it) } -> ALL_WORDS - SOUND_ALIKE_PENALTY
        else -> typoDistance(q.soundWords, text.soundWords)?.let { TYPOS - it.coerceAtMost(TYPOS - AUTHOR - 1) }
    }

    /** Total typos when every query word is near some text word, else null. */
    private fun typoDistance(queryWords: List<String>, textWords: List<String>): Int? {
        var total = 0
        for (word in queryWords) {
            val allowed = typoThreshold(word.length)
            val best = textWords.minOfOrNull { levenshtein(word, it, allowed) } ?: return null
            if (best > allowed) return null
            total += best
        }
        return total
    }

    // Sound-alike letters already absorb the common confusions, so typos stay tight:
    // none in words of up to 2 letters, 1 up to 6, 2 from 7 letters on.
    private fun typoThreshold(wordLength: Int): Int = when {
        wordLength <= 2 -> 0
        wordLength <= 6 -> 1
        else -> 2
    }

    /**
     * Matching books, best first; if there are none, the [CLOSEST_LIMIT] titles nearest the query
     * (most of its words, closest spelling), which [isClosest] tells apart.
     */
    fun search(books: Collection<Book>, query: String): List<Book> {
        if (query.isBlank()) return books.toList()
        val scored = books.map { it to relevanceScore(it, query) }
        val matches = scored.filter { it.second >= MATCH_THRESHOLD }
        if (matches.isNotEmpty()) return matches.sortedByDescending { it.second }.map { it.first }
        return books.map { it to closestScore(it, query) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(CLOSEST_LIMIT)
            .map { it.first }
    }

    /** The book was offered as a closest title, not as a match of [query]. */
    fun isClosest(book: Book, query: String): Boolean =
        query.isNotBlank() && relevanceScore(book, query) < MATCH_THRESHOLD

    /**
     * For titles that don't match: how many query words are found (each with up to one more typo
     * than a match allows), less their typos. At least half the words must be found.
     */
    private fun closestScore(book: Book, query: String): Int {
        val q = keys(query).soundWords
        if (q.isEmpty()) return 0
        val title = keys(book.title).soundWords
        var found = 0
        var typos = 0
        for (word in q) {
            if (word.length <= 2) continue
            val allowed = typoThreshold(word.length) + 1
            val best = title.minOfOrNull { levenshtein(word, it, allowed) } ?: continue
            if (best <= allowed) {
                found++
                typos += best
            }
        }
        val counted = q.count { it.length > 2 }.coerceAtLeast(1)
        if (found == 0 || found * 2 < counted) return 0
        return CLOSEST * (found * 100 - typos * 10).coerceAtLeast(1)
    }

    /** Edit distance, giving up (returning [limit] + 1) once it must exceed [limit]. */
    private fun levenshtein(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        val m = a.length
        val n = b.length
        var previous = IntArray(n + 1) { it }
        var current = IntArray(n + 1)
        for (i in 1..m) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..n) {
                current[j] = if (a[i - 1] == b[j - 1]) previous[j - 1]
                else 1 + minOf(previous[j], current[j - 1], previous[j - 1])
                rowMin = minOf(rowMin, current[j])
            }
            if (rowMin > limit) return limit + 1
            val swap = previous
            previous = current
            current = swap
        }
        return previous[n]
    }

    /**
     * Returns the range in [text] (original, un-normalized) that matches [query] after Arabic
     * normalization — or failing that, in sound-alike spelling — so the range can be used directly
     * for a highlight span.
     *
     * Handles tashkeel in the original text: the returned range covers the original characters
     * (including any stripped tashkeel) so the span aligns with what the user sees.
     * Returns null if query is empty or there is no match.
     */
    fun findHighlightRange(text: String, query: String): IntRange? {
        if (query.isEmpty()) return null
        return rangeIn(text, query, ::spellingOf) ?: rangeIn(text, query, ::soundOf)
    }

    private fun rangeIn(text: String, query: String, keyOf: (Char) -> String): IntRange? {
        val normalizedQuery = query.trim().map(keyOf).joinToString("")
        if (normalizedQuery.isEmpty()) return null

        // Build a parallel list: normalizedText[i] came from original index origPositions[i].
        // Tashkeel chars are dropped by the key, so they have no entry here.
        val origPositions = mutableListOf<Int>()
        val normalizedText = StringBuilder()
        for ((i, ch) in text.withIndex()) {
            val normCh = keyOf(ch)
            for (c in normCh) {
                normalizedText.append(c)
                origPositions.add(i)
            }
        }

        val matchStart = normalizedText.indexOf(normalizedQuery)
        if (matchStart < 0) return null
        val matchEnd = matchStart + normalizedQuery.length

        val origStart = origPositions[matchStart]
        // If matchEnd points past the last normalized char, the match runs to the end of the
        // original string (the remaining chars were all tashkeel after the last kept char).
        val origEnd = if (matchEnd < origPositions.size) origPositions[matchEnd] else text.length
        return origStart until origEnd
    }
}
