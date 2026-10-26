package com.shamela.apptheme.data.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.shamela.apptheme.data.util.ArabicNormalizer
import com.shamela.apptheme.data.util.ArabicSoundKey
import com.shamela.apptheme.domain.model.BookPage
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseHelper private constructor(val context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "AppDatabase"
        // v3: ArabicNormalizer.VERSION 2 (more characters unified) — indexed content is re-normalized.
        // v4: BookPageRows, an indexed book id -> page row map (see there).
        // v5: pages searched in sound-alike spelling (BookPage.COL_SOUND), and ArabicNormalizer.VERSION 3:
        //     the index is rebuilt from the books (an FTS5 table can't gain a column).
        const val DATABASE_VERSION = 5

        /**
         * Which FTS rows belong to which book. The FTS table can't look up its book id column (it's
         * not indexed), so deleting or checking one book scanned every page of the library: ~3 s
         * per book with 1000 books. This plain indexed table makes those lookups instant.
         */
        private const val ROWS_TABLE = "BookPageRows"
        private const val CREATE_ROWS_TABLE =
            "CREATE TABLE IF NOT EXISTS $ROWS_TABLE (book_id TEXT NOT NULL, page_rowid INTEGER NOT NULL)"
        private const val CREATE_ROWS_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_${ROWS_TABLE}_book ON $ROWS_TABLE(book_id)"

        /** Upper bound on rows returned by section/library searches, which carry full page text. */
        private const val MAX_SEARCH_RESULTS = 300

        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(context).also { INSTANCE = it }
            }
        }
    }

    init {
        // Searches and the reader keep reading while books are being indexed in the background.
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(database: SQLiteDatabase) {
        Log.e("DatabaseHelper", "onCreate: isCalled")
        database.execSQL(BookPage.CREATE_TABLE)
        database.execSQL(CREATE_ROWS_TABLE)
        database.execSQL(CREATE_ROWS_INDEX)
    }


    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d("DatabaseHelper", "onUpgrade: $oldVersion -> $newVersion")
        if (oldVersion < 5) {
            // New table layout: start empty. The library's indexing queue (run at every start for
            // books without pages) rebuilds every book's pages in the background.
            database.execSQL("DROP TABLE IF EXISTS ${BookPage.TABLE_NAME}")
            database.execSQL("DROP TABLE IF EXISTS $ROWS_TABLE")
            onCreate(database)
        }
    }

    suspend fun insertBookPage(page: BookPage): Boolean {
        return withContext(Dispatchers.IO) {
            val contentValues = ContentValues().apply {
                put(BookPage.COL_ID, page.id)
                put(BookPage.COL_BOOK_ID, page.bookId)
                put(BookPage.COL_BOOK_TITLE, page.bookTitle)
                put(BookPage.COL_HREF, page.href)
                put(BookPage.COL_CATEGORY, page.category)
                put(BookPage.COL_CONTENT, page.content)
                put(BookPage.COL_SOUND, ArabicSoundKey.fold(page.content))
            }

            return@withContext writableDatabase.insert(BookPage.TABLE_NAME, null, contentValues) > 0
        }
    }

    suspend fun insertBookPages(pages: List<BookPage>): Boolean {
        return withContext(Dispatchers.IO) {
            val db = writableDatabase
            try {
                db.beginTransaction()
                // Re-indexing a book (re-download / re-import) must not duplicate its pages.
                pages.map { it.bookId }.distinct().forEach { bookId -> deletePages(db, bookId) }
                val rowValues = ContentValues()
                val contentValues = ContentValues()
                for (page in pages) {
                    contentValues.clear()
                    contentValues.put(BookPage.COL_ID, page.id)
                    contentValues.put(BookPage.COL_BOOK_ID, page.bookId)
                    contentValues.put(BookPage.COL_BOOK_TITLE, page.bookTitle)
                    contentValues.put(BookPage.COL_HREF, page.href)
                    contentValues.put(BookPage.COL_CATEGORY, page.category)
                    contentValues.put(BookPage.COL_CONTENT, page.content)
                    contentValues.put(BookPage.COL_SOUND, ArabicSoundKey.fold(page.content))
                    val rowId = db.insert(BookPage.TABLE_NAME, null, contentValues)
                    if (rowId > 0) {
                        rowValues.clear()
                        rowValues.put("book_id", page.bookId)
                        rowValues.put("page_rowid", rowId)
                        db.insert(ROWS_TABLE, null, rowValues)
                    }
                }
                db.setTransactionSuccessful()
                true
            } catch (e: Exception) {
                Log.e("DatabaseHelper", "Bulk insert failed", e)
                false
            } finally {
                db.endTransaction()
            }
        }
    }

    suspend fun deleteBookPages(bookId: String) {
        withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                deletePages(db, bookId)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /** Deletes one book's pages through the row map (no scan of the whole FTS table). */
    private fun deletePages(db: SQLiteDatabase, bookId: String) {
        db.execSQL(
            "DELETE FROM ${BookPage.TABLE_NAME} WHERE rowid IN (SELECT page_rowid FROM $ROWS_TABLE WHERE book_id = ?)",
            arrayOf(bookId)
        )
        db.delete(ROWS_TABLE, "book_id = ?", arrayOf(bookId))
    }

    /** Ids of every book that has pages in the index. */
    suspend fun indexedBookIds(): Set<String> = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT DISTINCT book_id FROM $ROWS_TABLE", null)
            .use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    }

    suspend fun hasPages(bookId: String): Boolean {
        return withContext(Dispatchers.IO) {
            readableDatabase.rawQuery(
                "SELECT 1 FROM $ROWS_TABLE WHERE book_id = ? LIMIT 1",
                arrayOf(bookId)
            ).use { it.moveToFirst() }
        }
    }

    suspend fun searchBook(bookId: String, query: String): List<BookPage> =
        search(
            selection = "${BookPage.COL_BOOK_ID} = ? AND ${BookPage.COL_SOUND} MATCH ?",
            args = { phrase -> arrayOf(bookId, phrase) },
            query = query,
            withContent = true,
            limit = null,
        )

    suspend fun searchCategory(category: String, query: String): List<BookPage> =
        search(
            selection = "${BookPage.COL_CATEGORY} = ? AND ${BookPage.COL_SOUND} MATCH ?",
            args = { phrase -> arrayOf(category, phrase) },
            query = query,
            withContent = true,
            limit = MAX_SEARCH_RESULTS,
        )

    suspend fun searchLibrary(query: String): List<BookPage> =
        search(
            selection = "${BookPage.COL_SOUND} MATCH ?",
            args = { phrase -> arrayOf(phrase) },
            query = query,
            withContent = true,
            limit = MAX_SEARCH_RESULTS,
        )

    /**
     * Runs an FTS phrase query on the pages' sound-alike spelling, so "سهيه" finds "صحيح". The query
     * is normalized and folded exactly like the indexed text and escaped, so user input can never
     * break the MATCH syntax. Pages with the query in its exact spelling come first, then by rank
     * (among the [limit] best ranked).
     */
    @SuppressLint("Range")
    private suspend fun search(
        selection: String,
        args: (phrase: String) -> Array<String>,
        query: String,
        withContent: Boolean,
        limit: Int?,
    ): List<BookPage> {
        val phrase = FtsQuery.phrase(query)
        if (phrase == FtsQuery.EMPTY) return emptyList()
        return withContext(Dispatchers.IO) {
            val columns = listOf(
                BookPage.COL_HREF,
                BookPage.COL_BOOK_ID,
                BookPage.COL_CATEGORY,
                BookPage.COL_BOOK_TITLE,
                BookPage.COL_CONTENT,
            ).joinToString()
            // Ranked by the index alone: ordering on the page text in SQL would read every matching
            // page of the library. Exact spelling is put first among the returned pages below.
            val sql = "SELECT $columns FROM ${BookPage.TABLE_NAME} WHERE $selection ORDER BY rank" +
                (limit?.let { " LIMIT $it" } ?: "")
            val exact = FtsQuery.normalized(query)
            try {
                readableDatabase.rawQuery(sql, args(phrase)).use { cursor ->
                    val results = ArrayList<BookPage>(cursor.count)
                    while (cursor.moveToNext()) {
                        results.add(
                            BookPage(
                                href = cursor.getString(cursor.getColumnIndex(BookPage.COL_HREF)),
                                content = if (withContent)
                                    cursor.getString(cursor.getColumnIndex(BookPage.COL_CONTENT))
                                else "",
                                bookId = cursor.getString(cursor.getColumnIndex(BookPage.COL_BOOK_ID)),
                                category = cursor.getString(cursor.getColumnIndex(BookPage.COL_CATEGORY)),
                                bookTitle = cursor.getString(cursor.getColumnIndex(BookPage.COL_BOOK_TITLE))
                            )
                        )
                    }
                    if (withContent) results.sortedBy { !it.content.contains(exact) } else results
                }
            } catch (e: Exception) {
                Log.e("DatabaseHelper", "search failed for [$query]: ${e.message}")
                emptyList()
            }
        }
    }
}

/** Builds safe FTS5 phrase queries from free user input. */
object FtsQuery {
    const val EMPTY = "\"\""
    private val normalizer = ArabicNormalizer()

    /**
     * Normalizes and folds [query] like the indexed sound-alike text ([BookPage.COL_SOUND]) and
     * wraps it as an FTS5 string, doubling quotes.
     */
    fun phrase(query: String): String =
        "\"" + ArabicSoundKey.fold(normalized(query)).replace("\"", "\"\"") + "\""

    /** The normalized form of [query], for locating matches inside already-normalized content. */
    fun normalized(query: String): String = normalizer.normalize(query.trim())

    /**
     * Where [query] is in [content] (normalized page text): its exact spelling, or failing that its
     * sound-alike spelling; null if neither. The range is in [content], to show what the page says.
     */
    fun locate(content: String, query: String): IntRange? {
        val token = normalized(query)
        if (token.isEmpty()) return null
        var start = content.indexOf(token)
        if (start < 0) start = ArabicSoundKey.fold(content).indexOf(ArabicSoundKey.fold(token).trim())
        if (start < 0) return null
        val length = if (content.startsWith(token, start)) token.length else ArabicSoundKey.fold(token).trim().length
        return start until start + length
    }
}
