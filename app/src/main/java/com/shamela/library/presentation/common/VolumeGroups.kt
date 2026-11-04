package com.shamela.library.presentation.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.R
import com.shamela.library.data.remote.archive.ArchiveOrg
import com.shamela.library.domain.model.Book

/**
 * A row of a book list where the volumes of a scanned book ("<title> - ج 2", see [ArchiveOrg.bookOf])
 * sit in one card for the book, the way they are offered for download.
 */
sealed interface BookRow {
    val key: String

    data class Plain(val book: Book) : BookRow {
        override val key get() = book.id
    }

    /** A multi-volume scanned book; its [volumes] (book, volume number) in volume order. */
    data class VolumeSet(val title: String, val author: String, val volumes: List<Pair<Book, Int>>) : BookRow {
        override val key get() = "volumes-$title"
    }
}

/** [books] (already sorted) as rows: a scanned book's volumes gathered at the place of its first volume. */
fun bookRows(books: List<Book>): List<BookRow> {
    val volumes = books.mapNotNull { book -> ArchiveOrg.volumeOf(book)?.let { (title, number) -> Triple(title, number, book) } }
        .groupBy { it.first }
    if (volumes.isEmpty()) return books.map { BookRow.Plain(it) }
    val rows = ArrayList<BookRow>(books.size)
    val placed = HashSet<String>()
    for (book in books) {
        val title = ArchiveOrg.volumeOf(book)?.first
        if (title == null) {
            rows += BookRow.Plain(book)
        } else if (placed.add(title)) {
            val set = volumes.getValue(title).sortedBy { it.second }
            rows += BookRow.VolumeSet(title, book.author, set.map { it.third to it.second })
        }
    }
    return rows
}

/** What a volume inside a scanned book's card can do; the same as a book row of the list. */
class VolumeActions(
    val onClick: (Book) -> Unit,
    val onLongClick: (Book) -> Unit,
    val onFavorite: (Book) -> Unit,
    val onInfo: (Book) -> Unit,
    val isFavorite: (Book) -> Boolean,
)

/**
 * Lists [rows]: plain books through [bookRow] (runs of them as segmented blocks), scanned books as
 * a card like the download page's, opening to its volumes. While books are being selected, a tap on
 * the card's head selects (or unselects) all of its volumes; so does a long press.
 */
fun LazyListScope.bookRowItems(
    rows: List<BookRow>,
    expanded: Set<String>,
    selectedIds: Set<String>,
    onToggleExpanded: (String) -> Unit,
    onToggleVolumes: (List<Book>) -> Unit,
    volumeActions: VolumeActions,
    bookRow: @Composable LazyItemScope.(book: Book, modifier: Modifier, index: Int, count: Int) -> Unit,
) {
    // Each run of plain books between two cards is its own segmented block.
    val segments = IntArray(rows.size)
    val runSizes = IntArray(rows.size)
    var start = 0
    while (start < rows.size) {
        if (rows[start] !is BookRow.Plain) { start++; continue }
        var end = start
        while (end < rows.size && rows[end] is BookRow.Plain) end++
        for (i in start until end) { segments[i] = i - start; runSizes[i] = end - start }
        start = end
    }
    items(rows.size, key = { rows[it].key }) { index ->
        val itemModifier = Modifier.padding(horizontal = 16.dp).animateItem()
        when (val row = rows[index]) {
            is BookRow.Plain -> bookRow(row.book, itemModifier, segments[index], runSizes[index])
            is BookRow.VolumeSet -> VolumeSetCard(
                row = row,
                open = row.key in expanded,
                selectedIds = selectedIds,
                onHeadClick = { if (selectedIds.isNotEmpty()) onToggleVolumes(row.volumes.map { it.first }) else onToggleExpanded(row.key) },
                onHeadLongClick = { onToggleVolumes(row.volumes.map { it.first }) },
                actions = volumeActions,
                modifier = itemModifier,
            )
        }
    }
}

/** The ids to toggle so that all of [volumes] end up selected, or, when they all are, none. */
fun volumeSelectionToggles(volumes: List<Book>, selectedIds: Set<String>): List<String> {
    val ids = volumes.map { it.id }
    return if (ids.all { it in selectedIds }) ids else ids.filter { it !in selectedIds }
}

/** Same card as a result on the scanned-books download page (ArchiveSearchScreen). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VolumeSetCard(
    row: BookRow.VolumeSet,
    open: Boolean,
    selectedIds: Set<String>,
    onHeadClick: () -> Unit,
    onHeadLongClick: () -> Unit,
    actions: VolumeActions,
    modifier: Modifier,
) {
    val allSelected = row.volumes.all { it.first.id in selectedIds }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    allSelected -> MaterialTheme.colorScheme.secondaryContainer
                    open -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> MaterialTheme.colorScheme.surfaceContainer
                }
            )
            .combinedClickable(onClick = onHeadClick, onLongClick = onHeadLongClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (allSelected) {
                ListLeadingTile(
                    ShamelaIcons.Check,
                    shape = MaterialTheme.shapes.extraLarge,
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                ScannedSourceTile(row.title)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = AppFonts.content(MaterialTheme.typography.titleMedium),
                    maxLines = if (open) 6 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val author = row.author.takeUnless { it.isBlank() || it == "-" }
                Text(
                    text = listOfNotNull(stringResource(R.string.scanned_volumes_count, row.volumes.size), author).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                ShamelaIcons.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (open) 180f else 0f),
            )
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            row.volumes.forEach { (volume, number) ->
                val selected = volume.id in selectedIds
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .combinedClickable(onClick = { actions.onClick(volume) }, onLongClick = { actions.onLongClick(volume) })
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected) {
                        Icon(
                            ShamelaIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.archive_volume, number),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (volume.pageCount > 0) stringResource(R.string.book_pages, volume.pageCount)
                            else stringResource(R.string.archive_tap_to_read),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    InfoIconButton { actions.onInfo(volume) }
                    FavoriteIconButton(actions.isFavorite(volume)) { actions.onFavorite(volume) }
                }
            }
        }
    }
}
