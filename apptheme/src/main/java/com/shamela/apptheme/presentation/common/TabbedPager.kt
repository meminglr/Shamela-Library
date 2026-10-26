package com.shamela.apptheme.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch

/**
 * Tabs with swipeable pages — Compose's counterpart of Flutter's TabBar + TabBarView: a
 * row of [PillTabs] (the selected tab in a rounded pill that follows the swipe) over a [HorizontalPager].
 * The selection is owned by the caller ([selected]/[onSelect]), so it survives navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> TabbedPager(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
    /** Shown right after the tabs, on the same line (e.g. a matching dropdown button). */
    trailing: (@Composable () -> Unit)? = null,
    page: @Composable (T) -> Unit,
) {
    val initial = options.indexOf(selected).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initial) { options.size }
    val scope = rememberCoroutineScope()
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    // Caller changed the selection (e.g. restored state): follow it.
    LaunchedEffect(selected) {
        val index = options.indexOf(selected)
        if (index >= 0 && index != pagerState.currentPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(index)
        }
    }
    // User swiped: report the page that settled.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { index ->
            options.getOrNull(index)?.let { if (it != currentSelected) currentOnSelect(it) }
        }
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillTabs(
                labels = options.map { label(it) },
                icons = options.map { option -> icon?.invoke(option) },
                selectedIndex = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                // Follows the swipe: the pill stretches toward the next tab as the page moves.
                position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = if (trailing == null) PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                else PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            )
            trailing?.invoke()
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { index ->
            page(options[index])
        }
    }
}
