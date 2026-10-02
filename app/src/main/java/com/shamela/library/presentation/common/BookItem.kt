package com.shamela.library.presentation.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.R
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.search.BookSearchMatcher

/** [text] with the (Arabic-normalized) match of [query] highlighted; never fails on a miss. */
@Composable
internal fun buildHighlightedString(text: String, query: String): AnnotatedString {
    val highlight = SpanStyle(
        background = MaterialTheme.colorScheme.tertiaryContainer,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
    )
    return buildAnnotatedString {
        val range = BookSearchMatcher.findHighlightRange(text, query)
        if (range == null) {
            append(text)
        } else {
            append(text.substring(0, range.first))
            withStyle(highlight) { append(text.substring(range)) }
            append(text.substring(range.last + 1))
        }
    }
}

/**
 * M3 Expressive segmented-list shapes: the first and last rows of a group get the large corners,
 * rows in between the small ones, so a run of rows reads as one rounded block.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun segmentShapes(index: Int, count: Int): ListItemShapes =
    ListItemDefaults.segmentedShapes(index = index, count = count.coerceAtLeast(1))

/**
 * Filled containers for segmented rows, so each group reads as a rounded tonal block on the
 * surface (the default segmented colors blend into the background).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun segmentColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
)

/** Gap between the rows of a segmented list. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val SegmentGap = ListItemDefaults.SegmentedGap

/** Leading visual of list rows: an icon on an M3 Expressive shape (cookie, clover, ...). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ListLeadingTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialShapes.Cookie4Sided.toShape(),
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    }
}

/**
 * The single book row shared by every list: an M3 Expressive segmented list item with a shaped
 * cover tile, the title (up to two lines), "author · N pages" and trailing actions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookListItem(
    item: Book,
    highlightText: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    leading: @Composable () -> Unit = { ListLeadingTile(ShamelaIcons.MenuBook) },
    trailing: @Composable RowScope.() -> Unit,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = segmentShapes(index, count),
        colors = segmentColors(),
        modifier = modifier,
        leadingContent = leading,
        trailingContent = { Row(verticalAlignment = Alignment.CenterVertically, content = trailing) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val author = item.author.takeUnless { it.isBlank() || it == "-" }
                if (author != null) {
                    Text(
                        text = buildHighlightedString(author, highlightText),
                        style = AppFonts.content(MaterialTheme.typography.bodyMedium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = "  ·  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                if (item.pageCount > 0) {
                    Text(
                        text = stringResource(R.string.book_pages, item.pageCount),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
            }
        },
    ) {
        Text(
            text = buildHighlightedString(item.title, highlightText),
            style = AppFonts.content(MaterialTheme.typography.titleMedium),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoIconButton(onInfoClick: (() -> Unit)?) {
    if (onInfoClick != null) {
        IconButton(onClick = onInfoClick) {
            Icon(
                imageVector = ShamelaIcons.Info,
                contentDescription = stringResource(R.string.about_book),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Favorite toggle that morphs from a circle to a rounded square when checked (Expressive). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FavoriteIconButton(isFavorite: Boolean, onClick: () -> Unit) {
    IconToggleButton(
        checked = isFavorite,
        onCheckedChange = { onClick() },
        shapes = IconButtonDefaults.toggleableShapes(),
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(
            imageVector = if (isFavorite) ShamelaIcons.Favorite else ShamelaIcons.FavoriteBorder,
            contentDescription = stringResource(R.string.nav_favorite),
        )
    }
}

@Composable
fun BookItem(
    modifier: Modifier,
    icon: @Composable () -> Unit = {
        Icon(
            imageVector = ShamelaIcons.NavigateForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    },
    item: Book,
    highlightText: String = "",
    onInfoClick: (() -> Unit)? = null,
    onClick: () -> Unit = {},
    index: Int = 0,
    count: Int = 1,
) {
    BookListItem(
        item = item,
        highlightText = highlightText,
        index = index,
        count = count,
        onClick = onClick,
        modifier = modifier,
    ) {
        InfoIconButton(onInfoClick)
        icon()
    }
}

@Composable
fun FavoriteBookItem(
    modifier: Modifier,
    item: Book,
    onFavoriteIconClicked: () -> Unit,
    highlightText: String = "",
    onInfoClick: (() -> Unit)? = null,
    onClick: () -> Unit = {},
    index: Int = 0,
    count: Int = 1,
) {
    BookListItem(
        item = item,
        highlightText = highlightText,
        index = index,
        count = count,
        onClick = onClick,
        modifier = modifier,
    ) {
        InfoIconButton(onInfoClick)
        FavoriteIconButton(item.isFavorite, onFavoriteIconClicked)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryBookItem(
    modifier: Modifier,
    item: Book,
    onFavoriteIconClicked: () -> Unit,
    onSwipeOut: () -> Unit,
    highlightText: String = "",
    isSelected: Boolean = false,
    onInfoClick: (() -> Unit)? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    index: Int = 0,
    count: Int = 1,
) {
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onSwipeOut()
            }
            // Don't dismiss the item; let the confirmation dialog decide.
            // The card snaps back, and the actual removal happens via state update on confirm.
            false
        },
    )
    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier,
        backgroundContent = {
            val active = swipeState.targetValue != SwipeToDismissBoxValue.Settled
            val color by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
                label = "swipe background"
            )
            val scale by animateFloatAsState(targetValue = if (active) 1.15f else 0.85f, label = "swipe icon scale")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = ShamelaIcons.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.scale(scale)
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
    ) {
        BookListItem(
            item = item,
            highlightText = highlightText,
            index = index,
            count = count,
            onClick = onClick,
            onLongClick = onLongClick,
            selected = isSelected,
            leading = {
                AnimatedContent(targetState = isSelected, label = "selection leading") { selected ->
                    if (selected) {
                        ListLeadingTile(
                            icon = ShamelaIcons.Check,
                            shape = MaterialTheme.shapes.extraLarge,
                            container = MaterialTheme.colorScheme.primary,
                            content = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        ListLeadingTile(ShamelaIcons.MenuBook)
                    }
                }
            },
        ) {
            InfoIconButton(onInfoClick)
            FavoriteIconButton(item.isFavorite, onFavoriteIconClicked)
        }
    }
}
