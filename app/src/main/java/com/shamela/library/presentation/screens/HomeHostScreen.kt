package com.shamela.library.presentation.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.RectangleShape
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
import com.shamela.apptheme.presentation.theme.Motion
import com.shamela.apptheme.presentation.common.ProgressiveReveal
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
/** The top bar's padding; the bottom is left to [LocalFloatingBarInset] so lists reach the screen's edge. */
val LocalPaddingValues = compositionLocalOf { PaddingValues() }
/**
 * How much of the screen's bottom edge is covered: the system's navigation bar and everything
 * floating above it (the navigation bar or a screen's toolbar, the downloads and deletion bars).
 * Content is laid out behind them, so scrolling content pads its end by this to scroll clear.
 */
val LocalFloatingBarInset = compositionLocalOf { 0.dp }

/**
 * A screen's floating toolbar (e.g. the selection's actions), shown in the navigation bar's place
 * in the host's bottom stack, so the downloads bar always stays above it. Set with [FloatingToolbar].
 */
class BottomToolbarSlot {
    private var owner: Any? = null
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    fun show(owner: Any, content: @Composable () -> Unit) {
        this.owner = owner
        this.content = content
    }

    fun hide(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            content = null
        }
    }
}
val LocalBottomToolbar = compositionLocalOf { BottomToolbarSlot() }

/** Shows [content] in the host's bottom stack while [visible] (in the navigation bar's place). */
@Composable
fun FloatingToolbar(visible: Boolean, content: @Composable () -> Unit) {
    val slot = LocalBottomToolbar.current
    val owner = remember { Any() }
    // Every recomposition hands over the current lambda, so the toolbar shows the screen's latest state.
    SideEffect { if (visible) slot.show(owner, content) else slot.hide(owner) }
    DisposableEffect(slot) { onDispose { slot.hide(owner) } }
}

/** What the bottom stack ends with. */
private enum class BottomBar { Toolbar, Navigation, None }

/** Whether [entry]'s screen has the home top bar and the navigation bar. */
private fun showsBars(entry: NavBackStackEntry?): Boolean =
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


/** What a [HomePage] needs from the host to draw its top bar. */
private class HomePageHost(val navController: NavController, val lastTab: IntArray)
private val LocalHomePageHost = staticCompositionLocalOf<HomePageHost?> { null }

/** Corner radius of a page while it moves: shrunk by the back gesture, or scaling in or out. */
private val PageCorner = 28.dp
private val PageElevation = 8.dp

/**
 * A destination's page: opaque, its corners rounding while it is shrunk by the predictive back
 * gesture (or scales in and out), with the home top bar on the screens that have one. The bar is
 * part of the page, so the page revealed by the back gesture already shows its own bar.
 */
@Composable
fun AnimatedVisibilityScope.HomePage(entry: NavBackStackEntry, content: @Composable () -> Unit) {
    // Tab pages slide side by side like pager pages, with square corners and no shadow; only
    // screens opened over them round and lift while they move (opening, going back).
    val lifts = tabIndex(entry) < 0
    // Read only in the layer below, so a moving page is redrawn by the render thread each frame
    // instead of being recomposed (and its shadow and outline rebuilt) on the main thread.
    val corner = transition.animateDp(label = "pageCorner") {
        if (it == EnterExitState.Visible || !lifts) 0.dp else PageCorner
    }
    // Both pages share one color: a shadow while it moves marks the edge of the one on top.
    val elevation = transition.animateDp(label = "pageElevation") {
        if (it == EnterExitState.Visible || !lifts) 0.dp else PageElevation
    }
    val host = LocalHomePageHost.current
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val radius = corner.value
                shape = if (radius > 0.dp) RoundedCornerShape(radius) else RectangleShape
                clip = radius > 0.dp
                shadowElevation = elevation.value.toPx()
            }
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // The page's list rows fill in a row per frame instead of all in its first frame.
        ProgressiveReveal {
            if (host != null && showsBars(entry)) PageWithTopBar(entry, host, content) else content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PageWithTopBar(entry: NavBackStackEntry, host: HomePageHost, content: @Composable () -> Unit) {
    val navController = host.navController
    // The bar names the page's own tab; screens opened from a tab (search results) keep the tab's.
    val current = remember(entry.id) {
        val route = entry.destination.route
        if (route == Settings.route) Settings
        else destination.firstOrNull { it.route == route } ?: destination[host.lastTab[0]]
    }
    val onSettings = current == Settings
    // Expressive flexible app bar: large title that collapses as the content scrolls; its state
    // is the page's own, so one tab's collapsed bar doesn't open the next tab collapsed.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    // In landscape the large title alone took almost half the screen: a one-line bar there.
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val title: @Composable () -> Unit = {
        Text(
            text = stringResource(current.label),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    val navigationIcon: @Composable () -> Unit = {
        if (onSettings) {
            IconButton(onClick = { navController.popBackStack() }, shapes = IconButtonDefaults.shapes()) {
                Icon(ShamelaIcons.NavigateBack, contentDescription = stringResource(com.shamela.apptheme.R.string.back))
            }
        }
    }
    val actions: @Composable RowScope.() -> Unit = {
        current.actionIcon?.let { icon ->
            IconButton(
                onClick = { current.onActionClick() },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(icon, contentDescription = stringResource(R.string.search_book_names))
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
    }
    Scaffold(
        // Only the large bar collapses; with the one-line bar the behavior, having no bar to
        // collapse, would swallow every scroll.
        modifier = if (compactHeight) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        topBar = {
            if (compactHeight) TopAppBar(
                title = title,
                navigationIcon = navigationIcon,
                actions = actions,
                colors = colors,
            ) else MediumFlexibleTopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                colors = colors,
                navigationIcon = navigationIcon,
                actions = actions,
            )
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        // The bottom is left to LocalFloatingBarInset, so lists reach the screen's edge.
        CompositionLocalProvider(
            LocalPaddingValues provides PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
            ),
        ) { content() }
    }
}

/*
 * Page transitions. Between the navigation bar's tabs the pages move exactly like the in-page tabs
 * (TabbedPager's HorizontalPager): side by side, both across the whole width, with the same curve, in the direction of the tabs' order. Opening a screen follows Android's own (14+)
 * activity transition: the new page slides in from the reading end while the old one drifts a
 * quarter of the way (parallax); going back is the reverse, and the predictive back gesture scrubs
 * it (the page shrinks a little, rounds its corners and follows the finger). Nothing bounces.
 *
 * Opening a screen and switching tabs (and the navigation bar with them) use Material's spring
 * for full-screen transitions; going back uses Android's own predictive back curve over Material's
 * Long2 length, which traces close to that spring (see Motion). The page shrinks to 90%, as in
 * Android's predictive back guidance.
 */

private fun tabIndex(entry: NavBackStackEntry) = destination.indexOfFirst { it.route == entry.destination.route }


private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() =
    tabIndex(initialState) >= 0 && tabIndex(targetState) >= 0

/** Which way the pages move: forward (the new page from the reading end) or back. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.direction(pop: Boolean): SlideDirection {
    val from = tabIndex(initialState)
    val to = tabIndex(targetState)
    val forward = if (from >= 0 && to >= 0) to > from else !pop
    return if (forward) SlideDirection.Start else SlideDirection.End
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pageEnter(pop: Boolean): EnterTransition {
    val dir = direction(pop)
    // In-page tabs (TabbedPager) use the same spring, so all tabs move alike.
    return if (betweenTabs()) slideIntoContainer(dir, Motion.page())
    else if (dir == SlideDirection.Start) slideIntoContainer(dir, Motion.page())
    else slideIntoContainer(dir, Motion.back()) { it / 4 }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pageExit(pop: Boolean): ExitTransition {
    val dir = direction(pop)
    return if (betweenTabs()) slideOutOfContainer(dir, Motion.page())
    else if (dir == SlideDirection.Start) slideOutOfContainer(dir, Motion.page()) { it / 4 }
    else slideOutOfContainer(dir, Motion.back()) + scaleOut(Motion.back(), targetScale = 0.9f)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeHostScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // Computed from the back stack entry (no state written during composition, which cost an
    // extra recomposition of the whole host on every navigation).
    val showBars = remember(navBackStackEntry) { showsBars(navBackStackEntry) }

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
    val pageHost = remember(navController) { HomePageHost(navController, lastTab) }
    val bottomToolbar = remember { BottomToolbarSlot() }
    // The bottom stack's measured height (bars, toolbar and the gaps between them).
    val density = LocalDensity.current
    var bottomStackHeight by remember { mutableStateOf(0.dp) }
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Lists are laid out down to the screen's edge (behind the system bar too) and pad their
    // end by everything floating there instead.
    val floatingBarInset =
        if (bottomStackHeight > 0.dp) navBarBottomGap(systemBottom) + bottomStackHeight
        else systemBottom
    com.shamela.library.presentation.common.ProvideSourceBadgeInfo {
    CompositionLocalProvider(
        // Screens without the home top bar draw their own below the status bar; pages with it
        // provide their bar's padding instead (PageWithTopBar).
        LocalPaddingValues provides WindowInsets.systemBars
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues(),
        LocalFloatingBarInset provides floatingBarInset,
        LocalBottomToolbar provides bottomToolbar,
        LocalHomePageHost provides pageHost,
    ) {
      Box(
          Modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.surface)
      ) {
        NavHost(
            navController = navController,
            startDestination = NavigationGraphs.HOME_GRAPH_ROUTE,
            // On tablets and in landscape, lists stay a readable width instead of stretching
            // across the whole screen.
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            enterTransition = { pageEnter(pop = false) },
            exitTransition = { pageExit(pop = false) },
            popEnterTransition = { pageEnter(pop = true) },
            popExitTransition = { pageExit(pop = true) },
        ) {
            homeGraph(navController)
        }
        // Everything floating at the bottom, in one column so nothing can overlap: book
        // deletions, then downloads, then the navigation bar or the screen's own toolbar. Its
        // height is what lists leave clear below their last row.
        val currentRoute = currentDestination?.route
        val toolbarContent = bottomToolbar.content
        // While the toolbar animates out its content is already gone: keep showing the last one.
        var lastToolbar by remember { mutableStateOf(toolbarContent) }
        if (toolbarContent != null) lastToolbar = toolbarContent
        val bottomBar = when {
            toolbarContent != null -> BottomBar.Toolbar
            showBars -> BottomBar.Navigation
            else -> BottomBar.None
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBarBottomGap(systemBottom))
                .padding(horizontal = NavBarSideInset)
                .widthIn(max = NavBarMaxWidth)
                .fillMaxWidth()
                .onSizeChanged { bottomStackHeight = with(density) { it.height.toDp() } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            com.shamela.library.presentation.common.DeletionBar(Modifier.padding(bottom = 8.dp))
            // Book details shows its own download state above its action button, so the bar
            // would only cover it there.
            if (currentRoute != BookDetails.route) DownloadsBar(Modifier.padding(bottom = 12.dp))
            // The navigation bar leaves and comes back with the page: same length and curve as the
            // page transition, sliding all the way off the bottom edge (its height plus the gap
            // below it) instead of snapping away. A screen's toolbar swaps with it the same way.
            val offScreen = with(density) { navBarBottomGap(systemBottom).roundToPx() }
            // Slide only: it leaves the screen anyway, and fading the shadowed capsules needs an
            // offscreen layer every frame.
            val barEnter = slideInVertically(Motion.page()) { it + offScreen }
            val barExit = slideOutVertically(Motion.page()) { it + offScreen }
            Box(contentAlignment = Alignment.BottomCenter) {
                androidx.compose.animation.AnimatedVisibility(bottomBar == BottomBar.Toolbar, enter = barEnter, exit = barExit) {
                    lastToolbar?.invoke()
                }
                androidx.compose.animation.AnimatedVisibility(bottomBar == BottomBar.Navigation, enter = barEnter, exit = barExit) {
                    FloatingNavBar(
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
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "navIndicator",
                )
                val indicatorAlpha by androidx.compose.animation.core.animateFloatAsState(
                    if (selectedTab >= 0) 1f else 0f, label = "navIndicatorAlpha",
                )
                // The selected tab's capsule; offset follows the layout direction, like the row below.
                Box(
                    Modifier
                        // Read in the placement and draw phases: the tabs don't recompose every frame.
                        .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
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
