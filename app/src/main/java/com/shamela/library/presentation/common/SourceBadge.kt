@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.common

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.R
import com.shamela.library.data.catalog.BookSource
import com.shamela.library.data.catalog.CatalogSources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** "1", "2" or "1·2": the catalog number(s) of a book. */
fun sourceNumbers(sources: Set<BookSource>): String =
    sources.sortedBy { it.number }.joinToString("·") { it.number.toString() }

/** Container and content colors of a source: one tone per library, a third for books in both. */
@Composable
fun sourceColors(sources: Set<BookSource>): Pair<Color, Color> = with(MaterialTheme.colorScheme) {
    when {
        sources.size > 1 -> secondaryContainer to onSecondaryContainer
        BookSource.Site in sources -> tertiaryContainer to onTertiaryContainer
        else -> primaryContainer to onPrimaryContainer
    }
}

@Composable
fun sourceDescription(sources: Set<BookSource>): String = when {
    sources.size > 1 -> stringResource(R.string.source_both)
    BookSource.Site in sources -> stringResource(R.string.source_shamela)
    else -> stringResource(R.string.source_library)
}

/**
 * Leading tile of a book row: the book icon, with the number of each library the book comes from
 * (Kütüphane 1, Kütüphane 2) in small round badges at its bottom-right corner. A book of neither
 * catalog (imported from elsewhere) has no badge, nor any book when [SourceBadges] are turned off.
 */
@Composable
fun SourceTile(bookId: String, modifier: Modifier = Modifier) {
    val index by CatalogSources.index.collectAsStateWithLifecycle()
    val shown by SourceBadges.visible.collectAsStateWithLifecycle()
    val sources = if (shown) index.sources[bookId].orEmpty() else emptySet()
    val description = if (sources.isEmpty()) "" else sourceDescription(sources)
    Box(modifier.semantics { if (description.isNotEmpty()) contentDescription = description }) {
        ListLeadingTile(ShamelaIcons.MenuBook)
        if (sources.isNotEmpty()) {
            Row(
                // The bottom-right corner in either reading direction.
                Modifier
                    .align(AbsoluteAlignment.BottomRight)
                    .offset(x = 4.dp, y = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                sources.sortedBy { it.number }.forEach { CornerBadge(it) }
            }
        }
    }
}

@Composable
private fun CornerBadge(source: BookSource) {
    // Strong tones, to stand out on the pale tile.
    val (container, content) = with(MaterialTheme.colorScheme) {
        if (source == BookSource.Site) tertiary to onTertiary else primary to onPrimary
    }
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = source.number.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp),
            color = content,
        )
    }
}

/** Small round number of a source, for inline use (book details). */
@Composable
fun SourceNumberBadge(sources: Set<BookSource>, modifier: Modifier = Modifier) {
    val (container, content) = sourceColors(sources)
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .size(width = if (sources.size > 1) 40.dp else 28.dp, height = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = sourceNumbers(sources), style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/** Whether book rows show the library number badges: a setting, on by default. */
object SourceBadges {
    private const val PREFS = "source_badges"
    private const val KEY_VISIBLE = "visible"

    private val _visible = MutableStateFlow(true)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /** Loads the stored choice (at app start). */
    fun init(context: Context) {
        _visible.value = prefs(context).getBoolean(KEY_VISIBLE, true)
    }

    fun setVisible(context: Context, visible: Boolean) {
        _visible.value = visible
        prefs(context).edit().putBoolean(KEY_VISIBLE, visible).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
