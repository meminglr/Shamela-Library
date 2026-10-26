package com.shamela.library.presentation.screens

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.TopAppBarState
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import com.shamela.library.presentation.common.DownloadsBar
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineHeightStyle
import com.shamela.library.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shamela.apptheme.presentation.common.DefaultTopBar
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.presentation.navigation.BookDetails
import com.shamela.library.presentation.navigation.Download
import com.shamela.library.presentation.navigation.Favorite
import com.shamela.library.presentation.navigation.Library
import com.shamela.library.presentation.navigation.NavigationGraphs
import com.shamela.library.presentation.navigation.Search
import com.shamela.library.presentation.navigation.SearchResults
import com.shamela.library.presentation.navigation.SectionBooks
import com.shamela.library.presentation.navigation.Settings
import com.shamela.library.presentation.navigation.homeGraph

private val destination = listOf(
    Library,
    Download,
    Favorite,
    Search,
    Settings,
)
val LocalPaddingValues = compositionLocalOf { PaddingValues() }
/**
 * How much of the content's bottom edge the floating navigation bar covers. Content is laid out
 * behind the bar, so scrolling lists pad their end by this to let the last item scroll clear of it.
 */
val LocalFloatingBarInset = compositionLocalOf { 0.dp }
/** A screen sets this while it shows its own floating toolbar in the navigation bar's place. */
val LocalHideNavBar = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeHostScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // Computed from the back stack entry (no state written during composition, which cost an
    // extra recomposition of the whole host on every navigation).
    val showBars = remember(navBackStackEntry) {
        val entry = navBackStackEntry
        // Before the nav graph is set up (the very first frame) there is no route yet: that is the
        // home screen, so keep the bars. Hiding them there made the content start at the top of the
        // screen and then slide down when the bars appeared.
        if (entry == null) true
        else if (entry.destination.parent?.route == NavigationGraphs.HOME_GRAPH_ROUTE) {
            when (entry.destination.route) {
                SearchResults.route -> entry.arguments?.getString("categoryName") == "all"
                SectionBooks.route, BookDetails.route -> false
                else -> true
            }
        } else false
    }

    // Screens opened from a tab (e.g. search results) keep that tab selected: remember the last tab
    // in a plain holder (not state), updated after composition.
    val lastTab = remember { intArrayOf(0) }
    val matchedTab = destination.indexOfFirst { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }
    val selectedScreen = if (matchedTab >= 0) matchedTab else lastTab[0]
    SideEffect { lastTab[0] = selectedScreen }
    val hideNavBar = remember { mutableStateOf(false) }
    // Expressive flexible app bar: large title that collapses as the content scrolls.
    // One app bar state per tab: a bar collapsed by scrolling one tab no longer opens the next
    // tab collapsed too.
    val topBarState = remember(selectedScreen) { TopAppBarState(-Float.MAX_VALUE, 0f, 0f) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state = topBarState)
    // The bar's real height (labels make it taller than the default toolbar), for the space lists
    // leave below their last row.
    val density = LocalDensity.current
    var navBarHeight by remember { mutableStateOf(FloatingToolbarDefaults.ContainerSize) }
    val floatingBarInset =
        if (showBars) navBarHeight + FloatingToolbarDefaults.ScreenOffset
        else 0.dp
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AnimatedVisibility(
                visible = showBars,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                val current = destination[selectedScreen]
                MediumFlexibleTopAppBar(
                    title = {
                        Text(
                            text = stringResource(current.label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    actions = {
                        current.actionIcon?.let { icon ->
                            IconButton(
                                onClick = { current.onActionClick() },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(icon, contentDescription = stringResource(R.string.nav_search))
                            }
                        }
                    },
                )
            }
        }
    ) {paddingValues ->
        CompositionLocalProvider(
            LocalPaddingValues provides paddingValues,
            LocalFloatingBarInset provides floatingBarInset,
            LocalHideNavBar provides hideNavBar,
        ) {
          Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = NavigationGraphs.HOME_GRAPH_ROUTE,
                // On tablets and in landscape, lists stay a readable width instead of stretching
                // across the whole screen.
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 840.dp)
                    .fillMaxSize(),
                enterTransition = { fadeIn(tween(350)) },
                exitTransition = { fadeOut(tween(350)) },
                popEnterTransition = { fadeIn(tween(350)) },
                popExitTransition = { fadeOut(tween(350)) }
            ) {
                homeGraph(navController)
            }
            // Download progress on every screen, just above the navigation bar. Book details shows
            // its own download state above its action button, so the bar would only cover it there.
            val currentRoute = currentDestination?.route
            // Book deletions show the same way, stacked above the downloads bar.
            androidx.compose.foundation.layout.Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = paddingValues.calculateBottomPadding() + floatingBarInset + 12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                com.shamela.library.presentation.common.DeletionBar()
                if (currentRoute != BookDetails.route) DownloadsBar()
            }
            AnimatedVisibility(
                visible = showBars && !hideNavBar.value,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = paddingValues.calculateBottomPadding() + FloatingToolbarDefaults.ScreenOffset),
            ) {
                FloatingNavBar(
                    modifier = Modifier.onSizeChanged { navBarHeight = with(density) { it.height.toDp() } },
                    selectedIndex = selectedScreen,
                    onSelect = { index ->
                        navController.navigate(destination[index].route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
          }
        }
    }
}
/** Size of the navigation bar relative to its full-width design; proportions stay the same. */
private const val NavBarScale = 0.85f

/** Share of the screen width the bar takes: the full-width design (screen less 32 dp), scaled. */
private const val NavBarWidthFraction = 0.79f

/**
 * Floating navigation bar: a wide pill above the content, the destinations spread evenly across
 * it, each an icon with its label right below. The selected one's icon sits in a primary-colored
 * pill and its label takes the primary color.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingNavBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        // A full pill, everything sized by [NavBarScale] so the bar keeps its proportions; the side
        // padding keeps the outer items clear of its rounded ends.
        modifier = modifier
            .widthIn(max = 560.dp * NavBarScale)
            .fillMaxWidth(NavBarWidthFraction),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 18.dp * NavBarScale, vertical = 14.dp * NavBarScale),
        // Theme color roles only (mixing in raw primary looked muddy): the list cards use
        // surfaceContainer, so the bar takes the highest container tone, the same one as the tab
        // switchers on these screens; the shadow lifts it off the content.
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        expandedShadowElevation = 6.dp,
    ) {
        destination.forEachIndexed { index, screen ->
            val selected = index == selectedIndex
            FloatingNavItem(
                selected = selected,
                icon = if (selected) screen.selectedIcon else screen.unSelectedIcon,
                label = stringResource(screen.label),
                onClick = { onSelect(index) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.FloatingNavItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val indicator by animateColorAsState(
        // The selected destination's icon sits in a pill in the theme's primary color.
        if (selected) colors.primary else colors.primary.copy(alpha = 0f),
        motion.defaultEffectsSpec(),
    )
    val iconTint by animateColorAsState(
        if (selected) colors.onPrimary else colors.onSurfaceVariant,
        motion.defaultEffectsSpec(),
    )
    val tint by animateColorAsState(
        if (selected) colors.primary else colors.onSurfaceVariant,
        motion.defaultEffectsSpec(),
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.large)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(vertical = 4.dp * NavBarScale),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 60.dp * NavBarScale, height = 36.dp * NavBarScale)
                .clip(CircleShape)
                .background(indicator),
            contentAlignment = Alignment.Center,
        ) {
            // The label below names the item, so the icon isn't announced separately.
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp * NavBarScale))
        }
        Spacer(Modifier.height(4.dp * NavBarScale))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MaterialTheme.typography.labelMedium.fontSize * NavBarScale,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            color = tint,
            maxLines = 1,
            softWrap = false,
        )
    }
}
