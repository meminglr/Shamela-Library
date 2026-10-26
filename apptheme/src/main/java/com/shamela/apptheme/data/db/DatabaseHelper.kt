package com.shamela.apptheme.data.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.shamela.apptheme.data.util.ArabicNormalizer
import com.shamela.apptheme.domain.model.BookPage
import com.shamela.apptheme.presentation.worker.BookMigrationWorker
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
        const val DATABASE_VERSION = 4

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
        private const val MIGRATION_WORK_NAME = "fts-renormalize"

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
        if (oldVersion < 4) {
            database.execSQL(CREATE_ROWS_TABLE)
            database.execSQL(CREATE_ROWS_INDEX)
            // One scan to map the pages indexed so far.
            database.execSQL(
                "INSERT INTO $ROWS_TABLE (book_id, page_rowid) SELECT ${BookPage.COL_BOOK_ID}, rowid FROM ${BookPage.TABLE_NAME}"
            )
        }
        if (oldVersion >= 3) return
        // Every version so far keeps the same FTS table; only the normalization rules changed.
        // Re-normalizing is idempotent, so one pass brings any older index up to date without
        // dropping it (dropping would silently disable search for every downloaded book).
        val migrationWorkRequest = OneTimeWorkRequestBuilder<BookMigrationWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MIGRATION_WORK_NAME, ExistingWorkPolicy.REPLACE, migrationWorkRequest)
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
            selection = "${BookPage.COL_BOOK_ID} = ? AND ${BookPage.COL_CONTENT} MATCH ?",
            args = { phrase -> arrayOf(bookId, phrase) },
            query = query,
            withContent = true,
            limit = null,
        )

    suspend fun searchCategory(category: String, query: String): List<BookPage> =
        search(
            selection = "${BookPage.COL_CATEGORY} = ? AND ${BookPage.COL_CONTENT} MATCH ?",
            args = { phrase -> arrayOf(category, phrase) },
            query = query,
            withContent = true,
            limit = MAX_SEARCH_RESULTS,
        )

    suspend fun searchLibrary(query: String): List<BookPage> =
        search(
            selection = "${BookPage.COL_CONTENT} MATCH ?",
            args = { phrase -> arrayOf(phrase) },
            query = query,
            withContent = true,
            limit = MAX_SEARCH_RESULTS,
        )

    /**
     * Runs an FTS phrase query. The query is normalized exactly like the indexed content (see
     * BookPreparationWorker) and escaped, so user input can never break the MATCH syntax.
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
            val columns = arrayOf(
                BookPage.COL_HREF,
                BookPage.COL_BOOK_ID,
                BookPage.COL_CATEGORY,
                BookPage.COL_BOOK_TITLE,
                BookPage.COL_CONTENT,
            )
            try {
                readableDatabase.query(
                    BookPage.TABLE_NAME,
                    columns,
                    selection,
                    args(phrase),
                    null,
                    null,
                    "rank",
                    limit?.toString()
                ).use { cursor ->
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
                    results
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

    /** Normalizes [query] like the indexed text and wraps it as an FTS5 string, doubling quotes. */
    fun phrase(query: String): String =
        "\"" + normalizer.normalize(query.trim()).replace("\"", "\"\"") + "\""

    /** The normalized form of [query], for locating matches inside already-normalized content. */
    fun normalized(query: String): String = normalizer.normalize(query.trim())
}
