package com.shamela.library.presentation.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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

/** Rounded tonal tile used as the leading visual of list rows (book "cover", section folder). */
@Composable
fun ListLeadingTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier
            .size(width = 40.dp, height = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    }
}

/**
 * The single book row layout shared by every list: cover tile, title (up to two lines), author and
 * page count, then trailing actions. Callers only add click/animation modifiers.
 */
@Composable
private fun BookRow(
    item: Book,
    highlightText: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = { ListLeadingTile(ShamelaIcons.MenuBook) },
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = buildHighlightedString(item.title, highlightText),
                style = AppFonts.content(MaterialTheme.typography.titleMedium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val author = item.author.takeUnless { it.isBlank() || it == "-" }
                if (author != null) {
                    Text(
                        text = buildHighlightedString(author, highlightText),
                        style = AppFonts.content(MaterialTheme.typography.bodyMedium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        trailing()
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

@Composable
private fun FavoriteIconButton(isFavorite: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        val tint by animateColorAsState(
            targetValue = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "favorite tint"
        )
        Icon(
            imageVector = if (isFavorite) ShamelaIcons.Favorite else ShamelaIcons.FavoriteBorder,
            contentDescription = stringResource(R.string.nav_favorite),
            tint = tint
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
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    },
    item: Book,
    highlightText: String = "",
    onInfoClick: (() -> Unit)? = null,
) {
    BookRow(item = item, highlightText = highlightText, modifier = modifier) {
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
) {
    BookRow(item = item, highlightText = highlightText, modifier = modifier) {
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
        val rowColor by animateColorAsState(
            targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            label = "selection background"
        )
        Surface(color = rowColor) {
            BookRow(
                item = item,
                highlightText = highlightText,
                modifier = modifier,
                leading = {
                    AnimatedContent(targetState = isSelected, label = "selection leading") { selected ->
                        if (selected) {
                            Box(
                                modifier = Modifier.size(width = 40.dp, height = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = ShamelaIcons.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
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
}
