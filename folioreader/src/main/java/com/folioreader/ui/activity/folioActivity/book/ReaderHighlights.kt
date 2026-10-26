@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.folioreader.ui.activity.folioActivity.book

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.folioreader.R
import com.folioreader.reader.Highlight
import com.folioreader.reader.HighlightColor
import com.folioreader.reader.SearchSession
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons

/** Arabic book text, laid out right-to-left whatever the UI language. */
@Composable
private fun bookTextStyle(base: androidx.compose.ui.text.TextStyle) =
    AppFonts.content(base).copy(textDirection = TextDirection.Rtl, textAlign = TextAlign.Start)

/** Round color choices for a highlight. */
@Composable
private fun HighlightColorRow(selected: HighlightColor, onSelect: (HighlightColor) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HighlightColor.entries.forEach { color ->
            val isSelected = color == selected
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color.argb))
                    .border(
                        if (isSelected) 3.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
                    .clickable { onSelect(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(ShamelaIcons.Check, null, tint = Color(0xFF1C1B1F), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Edit one highlight: its color (applied at once), a note, or delete it. The sheet doesn't dim the
 * page, so the colored passage stays visible above it.
 */
@Composable
fun HighlightEditSheet(
    highlight: Highlight,
    onColor: (HighlightColor) -> Unit,
    onSaveNote: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember(highlight.id) { mutableStateOf(highlight.note) }
    ModalBottomSheet(
        onDismissRequest = {
            // Closing the sheet keeps a typed note, like the system's own note fields do.
            if (note != highlight.note) onSaveNote(note)
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = Color.Transparent,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(
                text = highlight.text,
                style = bookTextStyle(MaterialTheme.typography.bodyLarge),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color(highlight.color.argb).copy(alpha = 0.45f))
                    .padding(12.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { HighlightColorRow(highlight.color, onColor) }
                FilledTonalIconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
                    Icon(ShamelaIcons.Delete, contentDescription = stringResource(R.string.delete_highlight), tint = MaterialTheme.colorScheme.error)
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text(stringResource(R.string.note_hint)) },
                textStyle = AppFonts.content(MaterialTheme.typography.bodyLarge, naturalAlign = true)
                    .copy(color = MaterialTheme.colorScheme.onSurface),
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSaveNote(note); onDismiss() },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.align(Alignment.End).padding(top = 12.dp),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

/** Every highlight of the book, in reading order; tapping one jumps to it. */
@Composable
fun HighlightsListSheet(
    highlights: List<Highlight>,
    chapterTitles: List<String?>,
    onOpen: (Highlight) -> Unit,
    onEdit: (Highlight) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(ShamelaIcons.BorderColor, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.highlights_title), style = MaterialTheme.typography.titleLarge)
            }
            if (highlights.isEmpty()) {
                Text(
                    stringResource(R.string.highlights_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                return@Column
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 560.dp),
            ) {
                items(highlights.sortedWith(compareBy({ it.chapterIndex }, { it.created })), key = { it.id }) { h ->
                    Surface(
                        onClick = { onOpen(h) },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
                            Box(
                                Modifier
                                    .padding(top = 4.dp)
                                    .size(width = 6.dp, height = 40.dp)
                                    .clip(CircleShape)
                                    .background(Color(h.color.argb))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                chapterTitles.getOrNull(h.chapterIndex)?.let {
                                    Text(
                                        it,
                                        style = bookTextStyle(MaterialTheme.typography.labelMedium),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                Text(
                                    h.text,
                                    style = bookTextStyle(MaterialTheme.typography.bodyLarge),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (h.note.isNotBlank()) {
                                    Text(
                                        h.note,
                                        style = AppFonts.content(MaterialTheme.typography.bodyMedium, naturalAlign = true),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    )
                                }
                            }
                            IconButton(onClick = { onEdit(h) }) {
                                Icon(ShamelaIcons.BorderColor, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kindle-style navigator shown while reading a search result: the query (tap for the results
 * list), "3 / 70", previous/next match and close.
 */
@Composable
fun SearchNavigatorBar(
    state: SearchSession.State,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBackToResults: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(ShamelaIcons.Close, contentDescription = stringResource(R.string.search_close))
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onBackToResults)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(ShamelaIcons.Search, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        state.query,
                        style = AppFonts.content(MaterialTheme.typography.titleSmall, naturalAlign = true),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    stringResource(R.string.search_counter, state.index + 1, state.results.size) +
                        " · " + stringResource(R.string.search_back_to_results),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onPrevious, enabled = state.index > 0) {
                Icon(ShamelaIcons.NavigateBack, contentDescription = stringResource(R.string.search_previous))
            }
            IconButton(onClick = onNext, enabled = state.index < state.results.lastIndex) {
                Icon(ShamelaIcons.NavigateForward, contentDescription = stringResource(R.string.search_next))
            }
        }
    }
}
