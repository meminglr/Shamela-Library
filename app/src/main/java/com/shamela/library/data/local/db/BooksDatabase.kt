package com.shamela.library.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.Quote

@Database(entities = [Book::class, Quote::class], version = 4, exportSchema = true)
abstract class BooksDatabase : RoomDatabase() {
    abstract val booksDao: BooksDao
    abstract val quotesDao: QuotesDao

    companion object {
        const val DATABASE_NAME = "BooksDatabase"

        /** Quote ids changed from `text.hashCode()` (Int) to a stable String key, plus an index on bookId. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Quote_new` (`text` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL, " +
                        "`pageHref` TEXT NOT NULL, `bookName` TEXT NOT NULL, `bookId` TEXT NOT NULL, " +
                        "`quoteId` TEXT NOT NULL, PRIMARY KEY(`quoteId`), FOREIGN KEY(`bookId`) " +
                        "REFERENCES `DownloadedBooks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `Quote_new` (`text`, `pageIndex`, `pageHref`, `bookName`, `bookId`, `quoteId`) " +
                        "SELECT `text`, `pageIndex`, `pageHref`, `bookName`, `bookId`, " +
                        "'legacy-' || `bookId` || '-' || `quoteId` FROM `Quote`"
                )
                db.execSQL("DROP TABLE `Quote`")
                db.execSQL("ALTER TABLE `Quote_new` RENAME TO `Quote`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Quote_bookId` ON `Quote` (`bookId`)")
            }
        }
    }
}
