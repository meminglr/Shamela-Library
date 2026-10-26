package com.shamela.library.domain.search

import com.shamela.library.domain.model.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for BookSearchMatcher.
 *
 * Regression tests (marked REGRESSION) must pass before and after every change.
 * Feature tests (marked FEATURE - PHASE 1) will fail until Phase 1 is implemented
 * and should turn green after Arabic normalization + author search are added.
 */
class BookSearchMatcherTest {

    private fun book(title: String, author: String = "مؤلف مجهول") =
        Book("id-$title", title, author, 100, "قسم")

    // ─── REGRESSION: basic title matching ────────────────────────────────────

    @Test // REGRESSION
    fun `exact title match returns true`() {
        val book = book("صحيح البخاري")
        assertTrue(BookSearchMatcher.matches(book, "صحيح البخاري"))
    }

    @Test // REGRESSION
    fun `title substring match returns true`() {
        val book = book("صحيح البخاري")
        assertTrue(BookSearchMatcher.matches(book, "البخاري"))
    }

    @Test // REGRESSION
    fun `query not present in title or author returns false`() {
        val book = book("صحيح البخاري", "محمد البخاري")
        assertFalse(BookSearchMatcher.matches(book, "الترمذي"))
    }

    @Test // REGRESSION
    fun `empty query matches every book`() {
        assertTrue(BookSearchMatcher.matches(book("أي كتاب"), ""))
    }

    // ─── FEATURE (Phase 1): author search ────────────────────────────────────

    @Test // FEATURE - PHASE 1
    fun `exact author name match returns true`() {
        val book = book("عنوان ما", author = "ابن تيمية")
        assertTrue(BookSearchMatcher.matches(book, "ابن تيمية"))
    }

    @Test // FEATURE - PHASE 1
    fun `partial author name match returns true`() {
        val book = book("عنوان ما", author = "ابن تيمية")
        assertTrue(BookSearchMatcher.matches(book, "تيمية"))
    }

    @Test // FEATURE - PHASE 1
    fun `query matching only author but not title returns true`() {
        val book = book("الرسالة", author = "الشافعي")
        assertTrue(BookSearchMatcher.matches(book, "الشافعي"))
    }

    // ─── FEATURE (Phase 1): Arabic normalization ─────────────────────────────

    @Test // FEATURE - PHASE 1
    fun `alef_hamza_above in query matches plain alef in title`() {
        // أ (U+0623) in query should match ا (U+0627) in title after normalization
        val book = book("الإمام") // starts with ا
        assertTrue(BookSearchMatcher.matches(book, "أمام")) // query uses أ
    }

    @Test // FEATURE - PHASE 1
    fun `alef_hamza_below in query matches plain alef in title`() {
        // إ (U+0625) in query should match ا (U+0627) in title after normalization
        val book = book("الاجتهاد")
        assertTrue(BookSearchMatcher.matches(book, "إجتهاد"))
    }

    @Test // FEATURE - PHASE 1
    fun `alef_madda in title matches plain alef in query`() {
        // آ (U+0622) in title normalizes to ا, so a plain ا query finds it
        val book = book("آداب النفس")
        assertTrue(BookSearchMatcher.matches(book, "اداب النفس"))
    }

    @Test // FEATURE - PHASE 1
    fun `tashkeel in query is stripped before matching`() {
        val book = book("صحيح البخاري")
        // Query has fatha/kasra diacritics that the stored title lacks
        assertTrue(BookSearchMatcher.matches(book, "صَحِيح"))
    }

    @Test // FEATURE - PHASE 1
    fun `tashkeel in title is stripped before matching`() {
        val book = book("صَحِيحُ البُخَارِيّ") // title itself has tashkeel
        assertTrue(BookSearchMatcher.matches(book, "صحيح البخاري"))
    }

    @Test // FEATURE - PHASE 1
    fun `teh_marbuta in title matches heh in query`() {
        // ة (U+0629) normalizes to ه (U+0647)
        val book = book("قصة النبي") // title uses ة
        assertTrue(BookSearchMatcher.matches(book, "قصه النبي")) // query uses ه
    }

    @Test // FEATURE - PHASE 1
    fun `teh_marbuta in query matches teh_marbuta in title`() {
        // both sides normalize ة→ه, so they still match each other
        val book = book("الرسالة")
        assertTrue(BookSearchMatcher.matches(book, "الرسالة"))
    }

    @Test // FEATURE - PHASE 1
    fun `normalization applied to author field too`() {
        // Author has إ, query uses ا — should still match after normalization
        val book = book("عنوان", author = "إبن تيمية")
        assertTrue(BookSearchMatcher.matches(book, "ابن تيمية"))
    }

    // ─── FEATURE (Phase 3): non-contiguous word match ────────────────────────

    @Test // FEATURE - PHASE 3
    fun `all query words in title but not consecutively returns true`() {
        // "سير النبلاء" should match "سير أعلام النبلاء" — أعلام is a skipped middle word
        val book = book("سير أعلام النبلاء")
        assertTrue(BookSearchMatcher.matches(book, "سير النبلاء"))
    }

    // ─── FEATURE (Phase 3): typo / fuzzy tolerance ───────────────────────────

    @Test // FEATURE - PHASE 3
    fun `single character typo in one word matches`() {
        // "سبر" (ب) differs from "سير" (ي) by one edit — rest of query is exact
        val book = book("سير أعلام النبلاء")
        assertTrue(BookSearchMatcher.matches(book, "سبر أعلام النبلاء"))
    }

    // ─── findHighlightRange ───────────────────────────────────────────────────

    // ─── relevanceScore ───────────────────────────────────────────────────────

    private fun score(title: String, query: String, author: String = "مؤلف مجهول") =
        BookSearchMatcher.relevanceScore(book(title, author), query)

    @Test
    fun `exact title ranks above all-words, which ranks above a typo`() {
        assertTrue(score("سير أعلام النبلاء", "سير أعلام النبلاء") > score("سير أعلام النبلاء", "سير النبلاء"))
        assertTrue(score("سير أعلام النبلاء", "سير النبلاء") > score("سير أعلام النبلاء", "سبر أعلام النبلاء"))
    }

    @Test
    fun `typo in a title still ranks above an author-only match`() {
        assertTrue(score("سير أعلام النبلاء", "سبر أعلام النبلاء") > score("الرسالة", "الشافعي", author = "الشافعي"))
        assertTrue(score("الرسالة", "الشافعي", author = "الشافعي") > 0)
    }

    @Test
    fun `no match scores 0`() {
        assertEquals(0, score("صحيح البخاري", "الترمذي"))
    }

    @Test
    fun `exact title ranks above prefix which ranks above substring`() {
        val exact    = book("الفقه")
        val prefix   = book("الفقه الإسلامي")
        val contains = book("أسس الفقه")
        val query = "الفقه"
        assertTrue(
            BookSearchMatcher.relevanceScore(exact, query) >
            BookSearchMatcher.relevanceScore(prefix, query)
        )
        assertTrue(
            BookSearchMatcher.relevanceScore(prefix, query) >
            BookSearchMatcher.relevanceScore(contains, query)
        )
    }

    @Test
    fun `normalization applied in scoring`() {
        // query أساس (hamza above) scores the same against title اساس (plain alef)
        assertEquals(score("اساس البلاغة", "اساس"), score("اساس البلاغة", "أساس"))
    }

    // ─── Sound-alike letters, Persian keyboard, closest titles ───────────────

    @Test
    fun `sound-alike letters find the title, which ranks first`() {
        val bukhari = book("صحيح البخاري")
        val other = book("تغليق التعليق على صحيح البخاري")
        for (query in listOf("سحيح البخاري", "صهيح البخاري", "سهيه البهاري")) {
            assertTrue(query, BookSearchMatcher.matches(bukhari, query))
            assertEquals(query, bukhari, BookSearchMatcher.search(listOf(other, bukhari), query).first())
        }
        assertTrue(BookSearchMatcher.matches(book("تذكرة الحفاظ"), "تزكرة الحفاظ"))
        assertTrue(BookSearchMatcher.matches(book("القاموس المحيط"), "الكاموس المحيط"))
        assertTrue(BookSearchMatcher.matches(book("تفسير الطبري"), "طفسير التبري"))
        assertTrue(BookSearchMatcher.matches(book("فتح الباري"), "فته الباري"))
    }

    @Test
    fun `exact spelling ranks above sound-alike spelling`() {
        assertTrue(score("صحيح البخاري", "صحيح البخاري") > score("صحيح البخاري", "سحيح البخاري"))
    }

    @Test
    fun `persian keyboard letters match arabic ones exactly`() {
        assertEquals(score("صحيح البخاري", "صحيح البخاري"), score("صحيح البخاري", "صحیح البخاری"))
        assertEquals(score("كتاب التوحيد", "كتاب التوحيد"), score("كتاب التوحيد", "کتاب التوحید"))
    }

    @Test
    fun `nothing matching offers the closest titles`() {
        val books = listOf(book("فتح الباري شرح صحيح البخاري"), book("الأم"), book("رياض الصالحين"))
        val results = BookSearchMatcher.search(books, "فتح الباري شرح صحيح البخاري العمدة")
        assertEquals(books[0], results.first())
        assertTrue(BookSearchMatcher.isClosest(results.first(), "فتح الباري شرح صحيح البخاري العمدة"))
        assertFalse(BookSearchMatcher.isClosest(books[0], "فتح الباري"))
    }

    @Test
    fun `unrelated query offers nothing`() {
        assertTrue(BookSearchMatcher.search(listOf(book("صحيح البخاري")), "الرسالة القشيرية").isEmpty())
    }

    @Test
    fun `findHighlightRange marks a sound-alike match`() {
        val text = "صحيح البخاري"
        val range = BookSearchMatcher.findHighlightRange(text, "سحيح")
        assertEquals("صحيح", text.substring(range!!))
    }

    // ─── findHighlightRange ───────────────────────────────────────────────────

    @Test
    fun `findHighlightRange returns null for empty query`() {
        assertNull(BookSearchMatcher.findHighlightRange("صحيح البخاري", ""))
    }

    @Test
    fun `findHighlightRange returns null when query not found`() {
        assertNull(BookSearchMatcher.findHighlightRange("صحيح البخاري", "الترمذي"))
    }

    @Test
    fun `findHighlightRange returns range covering exact substring`() {
        val text = "صحيح البخاري"
        val range = BookSearchMatcher.findHighlightRange(text, "البخاري")
        assertNotNull(range)
        assertEquals("البخاري", text.substring(range!!))
    }

    @Test
    fun `findHighlightRange returns range in original text for normalized query`() {
        // query إجتهاد (hamza below) matches اجتهاد in الاجتهاد after normalization
        val text = "الاجتهاد"
        val range = BookSearchMatcher.findHighlightRange(text, "إجتهاد")
        assertNotNull(range)
        assertEquals("اجتهاد", text.substring(range!!))
    }

    @Test
    fun `findHighlightRange range covers tashkeel in original title`() {
        // Stored title has tashkeel; normalized query has none; highlight should span the
        // original characters including the stripped tashkeel so the span aligns visually.
        val text = "صَحِيح"
        val range = BookSearchMatcher.findHighlightRange(text, "صحيح")
        assertNotNull(range)
        assertEquals("صَحِيح", text.substring(range!!))
    }

    @Test
    fun `findHighlightRange works on author field for normalized query`() {
        // Author has إ, query uses ا
        val author = "إبن تيمية"
        val range = BookSearchMatcher.findHighlightRange(author, "ابن تيمية")
        assertNotNull(range)
        assertEquals("إبن تيمية", author.substring(range!!))
    }
}
