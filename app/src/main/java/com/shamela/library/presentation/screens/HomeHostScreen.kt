package com.shamela.library.presentation.screens

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBar
import com.shamela.library.R
import androidx.compose.ui.res.stringResource
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.shamela.library.presentation.navigation.AboutApp
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
import com.shamela.library.presentation.utils.NavigationUtils

private val destination = listOf(
    Library,
    Download,
    Favorite,
    Search,
    Settings,
)
val LocalPaddingValues = compositionLocalOf { PaddingValues() }
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeHostScreen() {
    val navController = rememberNavController()
    val screenBarsVisibility = remember { mutableStateOf(true) }
    screenBarsVisibility.value =
        if (NavigationUtils.parentGraphRoute(navController) == NavigationGraphs.HOME_GRAPH_ROUTE) {
            val currentRoute =  NavigationUtils.currentRoute(navController)
            val categoryName = navController.currentBackStackEntry?.arguments?.getString("categoryName")
            Log.d("Shamela", "HomeHostScreen: currentRout = $currentRoute")
            Log.d("Shamela", "HomeHostScreen: categoryName = $categoryName")
            if (currentRoute == SearchResults.route){
                categoryName == "all"
            }else{
                currentRoute != SectionBooks.route && currentRoute != AboutApp.route &&
                        currentRoute != BookDetails.route
            }
        }
        else
            false

    val shouldOpenAbout by AboutApp.pendingOpen.collectAsStateWithLifecycle()
    LaunchedEffect(shouldOpenAbout) {
        if (shouldOpenAbout) {
            navController.navigate(AboutApp.route)
            AboutApp.consumeOpen()
        }
    }

    var selectedScreen by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    // Expressive flexible app bar: large title that collapses as the content scrolls.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        bottomBar = {
            AnimatedVisibility(
                visible = screenBarsVisibility.value,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                ShortNavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    destination.forEachIndexed { index, screen ->
                        val isSelected =
                            currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        if (isSelected) {
                            selectedScreen = index
                        }
                        ShortNavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.unSelectedIcon,
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(screen.label),
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }
        },
        topBar = {
            AnimatedVisibility(
                visible = screenBarsVisibility.value,
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
                    navigationIcon = {
                        if (selectedScreen == 0) {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(ShamelaIcons.MoreVert, contentDescription = stringResource(R.string.about_app))
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.about_app)) },
                                        leadingIcon = { Icon(ShamelaIcons.Info, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            navController.navigate(AboutApp.route)
                                        }
                                    )
                                }
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
                    },
                )
            }
        }
    ) {paddingValues ->
        CompositionLocalProvider(LocalPaddingValues provides paddingValues) {

            NavHost(
                navController = navController,
                startDestination = NavigationGraphs.HOME_GRAPH_ROUTE,
                enterTransition = { fadeIn(tween(350)) },
                exitTransition = { fadeOut(tween(350)) },
                popEnterTransition = { fadeIn(tween(350)) },
                popExitTransition = { fadeOut(tween(350)) }
            ) {
                homeGraph(navController)
            }
        }
    }
}