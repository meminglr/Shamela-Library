package com.shamela.library.presentation.screens

import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
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

/** The navigation bar's tabs; settings open from the library's top bar instead. */
private val destination = listOf(
    Library,
    Download,
    Favorite,
    Search,
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
    // Settings open from the library's top bar, so the library tab stays selected there.
    val onSettingsScreen = currentDestination?.route == Settings.route
    val selectedScreen = when {
        onSettingsScreen -> 0
        matchedTab >= 0 -> matchedTab
        else -> lastTab[0]
    }
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
    var navBarHeight by remember { mutableStateOf(NavBarHeight) }
    // Lists are padded for the system bar already; the floating bar sits at least 4 dp above it.
    val floatingBarInset =
        if (showBars) navBarHeight + 4.dp
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
                val onSettings = onSettingsScreen
                val current = if (onSettings) Settings else destination[selectedScreen]
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
                    navigationIcon = {
                        if (onSettings) {
                            IconButton(onClick = { navController.popBackStack() }, shapes = IconButtonDefaults.shapes()) {
                                Icon(ShamelaIcons.NavigateBack, contentDescription = stringResource(com.shamela.apptheme.R.string.back))
                            }
                        }
                    },
                    actions = {
                        current.actionIcon?.let { icon ->
                            IconButton(
                                onClick = { current.onActionClick() },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(icon, contentDescription = stringResource(R.string.nav_search))
                            }
                        }
                        // Settings: from the library (home) screen's top-right corner.
                        if (current == Library) {
                            IconButton(
                                onClick = { navController.navigate(Settings.route) { launchSingleTop = true } },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(ShamelaIcons.SettingsOutlined, contentDescription = stringResource(R.string.nav_settings))
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
                    .padding(bottom = navBarBottomGap(paddingValues.calculateBottomPadding())),
            ) {
                FloatingNavBar(
                    modifier = Modifier.onSizeChanged { navBarHeight = with(density) { it.height.toDp() } },
                    selectedIndex = selectedScreen,
                    onSelect = { index ->
                        // Leaving settings: it is closed, so the library tab doesn't bring it back.
                        if (onSettingsScreen) navController.popBackStack()
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
/**
 * The navigation bar's measurements, after iOS 26's floating tab bar: a capsule 62 pt tall, inset
 * 21 pt from the screen's sides and bottom, 11 pt labels, the selected tab behind a capsule that
 * slides between the tabs.
 */
private val NavBarHeight = 62.dp
private val NavBarSideInset = 21.dp
private val NavBarBottomInset = 21.dp
/** Gap between the selected tab's capsule and the bar's edge. */
private val NavBarIndicatorInset = 4.dp
private val NavBarMaxWidth = 480.dp

/** Space from the screen's bottom edge to the bar: 21 dp, or just above the system's navigation bar. */
private fun navBarBottomGap(systemBottom: androidx.compose.ui.unit.Dp) = maxOf(NavBarBottomInset, systemBottom + 4.dp)

/** Gap between the tab capsule and the search button, as in iOS 26. */
private val NavBarSearchGap = 10.dp

/**
 * Floating navigation bar, after iOS 26's measurements: the tabs in a capsule and, beside it at the
 * end, search in a round button of its own. The selected tab, light icon and label, sits on a
 * capsule in the theme's accent ([navSelectedPill]) that slides to it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingNavBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = destination.filter { it != Search }
    val searchIndex = destination.indexOf(Search)
    Row(
        modifier = modifier
            .padding(horizontal = NavBarSideInset)
            .widthIn(max = NavBarMaxWidth)
            .fillMaxWidth()
            .height(NavBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCapsule(Modifier.weight(1f).fillMaxHeight()) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val itemWidth = maxWidth / tabs.size
                val selectedTab = tabs.indexOf(destination.getOrNull(selectedIndex))
                val indicatorOffset by androidx.compose.animation.core.animateDpAsState(
                    targetValue = itemWidth * selectedTab.coerceAtLeast(0),
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f),
                    label = "navIndicator",
                )
                val indicatorAlpha by androidx.compose.animation.core.animateFloatAsState(
                    if (selectedTab >= 0) 1f else 0f, label = "navIndicatorAlpha",
                )
                // The selected tab's capsule; offset follows the layout direction, like the row below.
                Box(
                    Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = indicatorAlpha }
                        .clip(CircleShape)
                        .background(navSelectedPill()),
                )
                Row(Modifier.fillMaxSize()) {
                    tabs.forEach { screen ->
                        val index = destination.indexOf(screen)
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
        }
        Spacer(Modifier.width(NavBarSearchGap))
        // Search: a round "island" the bar's height, at the end (left in Arabic, right in Turkish).
        val searchSelected = selectedIndex == searchIndex
        val searchTint by animateColorAsState(
            if (searchSelected) navSelectedContent() else MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.motionScheme.defaultEffectsSpec(),
        )
        GlassCapsule(Modifier.size(NavBarHeight)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(if (searchSelected) navSelectedPill() else Color.Transparent)
                    .selectable(selected = searchSelected, onClick = { onSelect(searchIndex) }, role = Role.Tab),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Search.selectedIcon,
                    contentDescription = stringResource(Search.label),
                    tint = searchTint,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

/**
 * The selected tab's capsule: the accent, deepened toward the page in dark mode (where the pastel
 * accent is light), so a light icon and label always read on it.
 */
@Composable
private fun navSelectedPill(): Color = with(MaterialTheme.colorScheme) {
    if (surface.luminance() < 0.5f) androidx.compose.ui.graphics.lerp(primary, surface, 0.45f) else primary
}

/** Light icon and label on [navSelectedPill]: the page color in light mode, the text color in dark. */
@Composable
private fun navSelectedContent(): Color = with(MaterialTheme.colorScheme) {
    if (surface.luminance() < 0.5f) onSurface else surface
}

/** The bar's capsule (a circle when square), in the theme's container color, lifted by a shadow. */
@Composable
private fun GlassCapsule(modifier: Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .shadow(elevation = 6.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            // A hairline edge, so the bar stays distinct on a black (dark theme) page too.
            .border(0.5.dp, colors.outlineVariant, CircleShape)
            .padding(NavBarIndicatorInset),
    ) { content() }
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
    // The selected item on the theme's dark primary capsule, the others in Android's muted tone.
    val tint by animateColorAsState(
        if (selected) navSelectedContent() else colors.onSurfaceVariant,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        // The label below names the item, so the icon isn't announced separately.
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
