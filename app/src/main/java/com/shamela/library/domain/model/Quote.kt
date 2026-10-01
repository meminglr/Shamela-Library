package com.shamela.library.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "Quote",
    foreignKeys = [ForeignKey(
        entity = Book::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class Quote(
    @ColumnInfo val text: String,
    @ColumnInfo val pageIndex: Int,
    @ColumnInfo val pageHref: String,
    @ColumnInfo val bookName: String,
    @ColumnInfo val bookId: String,
    @PrimaryKey val quoteId: String = idOf(bookId, pageHref, text),
) {
    companion object {
        /** Stable id: the same text on the same page of the same book is one quote, and quotes
         *  from different books never collide (the old `text.hashCode()` id did both). */
        fun idOf(bookId: String, pageHref: String, text: String): String =
            UUID.nameUUIDFromBytes("$bookId|$pageHref|$text".toByteArray()).toString()
    }
}
