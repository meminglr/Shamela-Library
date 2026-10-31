@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.folioreader.ui.activity.folioActivity.book


import com.folioreader.model.locators.SearchLocator
import com.folioreader.reader.ReadingPositionStore
import com.folioreader.reader.SearchSession
import com.folioreader.reader.HighlightStore
import com.folioreader.reader.Highlight
import androidx.compose.material3.Surface
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import com.folioreader.reader.ReaderSettings
import com.folioreader.reader.ReaderTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import android.view.WindowManager
import android.content.ContextWrapper
import android.content.Context
import android.app.Activity
import com.folioreader.reader.ReaderSettingsStore
import androidx.compose.ui.text.style.TextOverflow
import com.folioreader.R
import androidx.compose.ui.res.stringResource
import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folioreader.ui.activity.folioActivity.FolioActivity
import com.folioreader.ui.view.CustomWebView
import com.folioreader.util.AppUtil
import com.shamela.apptheme.presentation.common.LoadingScreen
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.AppTheme
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import kotlinx.coroutines.launch
import org.readium.r2.shared.Publication

@Composable
fun BookScreen(
    viewModel: BookViewModel = viewModel(),
    streamUrl: String,
    searchResult: Pair<String, String>,
    selectedChapter: String,
    settingsChanged: Int,
    publication: Publication,
    startPageHref: String,
    navigateToTableOfContent: (Int) -> Unit,
    navigateToSettings: (Int) -> Unit,
    navigateToSearchScreen: () -> Unit,
    navigateBack: () -> Unit,
    /** Shows a search result: switches chapter if needed, highlights and centers it. */
    onSearchLocator: (SearchLocator) -> Unit = {},
    /** Opens chapter [href] and runs [js] there (it returns the y position to scroll to). */
    onJump: (href: String, js: String) -> Unit = { _, _ -> },
    /** The book's first page is on screen, drawn at its reading position. */
    onFirstPageShown: () -> Unit = {},
) {
    val context = LocalContext.current
    val state = viewModel.state.collectAsStateWithLifecycle().value
    remember { ReaderSettingsStore.load(context) }
    val readerSettings by ReaderSettingsStore.settings.collectAsStateWithLifecycle()
    val isAppDark = AppTheme.isDarkTheme(context)
    val backgroundColor = readerSettings.backgroundColor(isAppDark)
    var showReaderSettings by remember { mutableStateOf(false) }
    val chapterTitles = remember(publication) { chapterTitles(publication) }
    val pagerState = rememberPagerState(pageCount = { publication.readingOrder.size })
    val scope = rememberCoroutineScope()
    val cachedWebViews = remember { mutableStateMapOf<Int, WebView>() }
    val bookKey = remember(publication) { publication.metadata.title.hashCode().toString() }
    remember(bookKey) { HighlightStore.open(context, bookKey) }
    val highlights by HighlightStore.highlights.collectAsStateWithLifecycle()
    val searchSession by SearchSession.state.collectAsStateWithLifecycle()
    // Back while reading a search result leaves search mode first (like Kindle); only the next back
    // closes the book. An edge swipe on the phone counts as back, and it used to drop the reader
    // right after returning from the results list.
    androidx.activity.compose.BackHandler(enabled = searchSession.isActive) { SearchSession.close() }
    var editingHighlightId by remember { mutableStateOf<String?>(null) }
    var showHighlights by remember { mutableStateOf(false) }

    // Where reading stopped inside the chapter. Restored once (unless the book was opened at a
    // specific place, e.g. a search result), and saved only after that, so opening the book
    // can't overwrite it with "top of the first chapter".
    val savedPosition = remember(bookKey) {
        if (startPageHref.isBlank() && searchResult.first.isBlank()) ReadingPositionStore.load(context, bookKey) else null
    }
    var positionReady by remember { mutableStateOf(false) }

    // Save last read on dispose
    DisposableEffect(Unit) {
        onDispose {
            val bookId = publication.metadata.title.hashCode()
            runCatching {
                publication.readingOrder[pagerState.currentPage].href?.let { lastReadHref ->
                    AppUtil.saveLastReadToSharedPreferences(
                        context = context,
                        bookId = bookId.toString(),
                        lastHref = lastReadHref
                    )
                }
            }.onFailure {
                Log.e("BookScreen", "Failed to save last read", it)
            }
        }
    }

    // Initial page logic
    LaunchedEffect(Unit) {
        val bookId = publication.metadata.title.hashCode()
        val lastReadHref = AppUtil.getLastReadFromSharedPreferences(context, bookId.toString())
        val target = when {
            startPageHref.isNotBlank() -> startPageHref
            savedPosition != null -> savedPosition.first
            lastReadHref.isNotBlank() -> lastReadHref
            else -> null
        }
        target?.let {
            val index = publication.readingOrder.indexOfFirst { item -> item.href == it }
            if (index != -1) pagerState.scrollToPage(index)
        }
        positionReady = true
    }

    // Scroll to search result, chapter, or update on settings change
    LaunchedEffect(searchResult, selectedChapter, settingsChanged) {
        when {
            searchResult.first.isNotBlank() -> {
                val index = publication.readingOrder.indexOfFirst { it.href == searchResult.first }
                if (index != -1) pagerState.scrollToPage(index)
            }
            selectedChapter.isNotBlank() -> {
                val index = publication.readingOrder.indexOfFirst {
                    it.href == selectedChapter.split("#").firstOrNull()
                }
                if (index != -1) pagerState.scrollToPage(index)
            }
            settingsChanged != 0 -> {
                val temp = pagerState.currentPage
                Log.e("BooksScreen", "pagerState.currentPage! : $temp")
                cachedWebViews.clear()
                viewModel.onEvent(BookEvent.ClearCachedPages)
                viewModel.onEvent(
                    BookEvent.OnChangeSelectedPage(
                        pageIndex = temp,
                        context = context,
                        fontFamilyCssClass = AppFonts.selectedFontFamilyCssClass(),
                        isNightMode = AppTheme.isDarkTheme(context),
                        fontSizeCssClass = AppFonts.selectedFontSizeCssClass(),
                        publication = publication,
                        streamUrl = streamUrl
                    )
                )
                pagerState.scrollToPage(temp)

            }
        }
    }

    // Apply reading settings live to every open page.
    LaunchedEffect(readerSettings) {
        val js = readerSettings.applyCssJs(isAppDark)
        cachedWebViews.values.forEach { webView ->
            webView.setBackgroundColor(backgroundColor.toInt())
            webView.evaluateJavascript(js, null)
        }
    }
    // Hiding harakat rewrites the page text, so pages are re-fetched instead of restyled.
    var lastHideHarakat by remember { mutableStateOf(readerSettings.hideHarakat) }
    LaunchedEffect(readerSettings.hideHarakat) {
        if (readerSettings.hideHarakat != lastHideHarakat) {
            lastHideHarakat = readerSettings.hideHarakat
            val current = pagerState.currentPage
            // The open WebViews stay; each reloads itself when its page's new HTML arrives.
            viewModel.onEvent(BookEvent.ClearCachedPages)
            viewModel.onEvent(
                BookEvent.OnChangeSelectedPage(
                    pageIndex = current,
                    context = context,
                    fontFamilyCssClass = AppFonts.selectedFontFamilyCssClass(),
                    isNightMode = isAppDark,
                    fontSizeCssClass = AppFonts.selectedFontSizeCssClass(),
                    publication = publication,
                    streamUrl = streamUrl
                )
            )
        }
    }
    // Keep the screen on and apply the reading brightness while the book is open.
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(readerSettings.keepScreenOn, readerSettings.brightness) {
        val window = activity?.window
        if (readerSettings.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.attributes = window?.attributes?.apply {
            screenBrightness = readerSettings.brightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.attributes = window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    // Observe current page
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.onEvent(
                BookEvent.OnChangeSelectedPage(
                    page,
                    AppFonts.selectedFontSizeCssClass(),
                    AppFonts.selectedFontFamilyCssClass(),
                    AppTheme.isDarkTheme(context),
                    context,
                    publication,
                    streamUrl
                )
            )
        }
    }

    // Evict WebViews that are far from the current page to prevent unbounded memory growth.
    // Keep ±3 so beyondViewportPageCount=2 pages are always within the live window.
    LaunchedEffect(pagerState.currentPage) {
        cachedWebViews.keys.toList().forEach { key ->
            if (kotlin.math.abs(key - pagerState.currentPage) > 3) {
                cachedWebViews[key]?.destroy()
                cachedWebViews.remove(key)
            }
        }
    }

    // The bars float over the page, in the page's colors, and the page's own top/bottom padding
    // follows their measured height: with the bars shown the text starts right below the top bar and
    // ends right above the bottom one (nothing hidden under them, no empty strip between), and with
    // them hidden it runs from the status bar to the navigation bar.
    val pageTheme = readerSettings.theme.resolve(isAppDark)
    ReaderSystemBarIcons(lightPage = !pageTheme.isDark)
    var topBarPx by remember { mutableIntStateOf(0) }
    var bottomBarsPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val statusBarTop = WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()
    // A hidden bar keeps reporting its last size, so the top follows the visibility state.
    val pageTop by androidx.compose.animation.core.animateDpAsState(
        if (state.isAppBarsVisible) maxOf(statusBarTop, with(density) { topBarPx.toDp() }) else statusBarTop,
        label = "page top"
    )
    val pageBottom by androidx.compose.animation.core.animateDpAsState(
        maxOf(navBarBottom, with(density) { bottomBarsPx.toDp() }), label = "page bottom"
    )
    Box(modifier = Modifier.fillMaxSize().background(Color(backgroundColor))) {
        // Pinch with two fingers to change the text size (live, and saved like the slider).
        var pinchStartSize by remember { mutableIntStateOf(0) }
        var pinchSize by remember { mutableStateOf<Int?>(null) }
        Box(Modifier.fillMaxSize()) {
        // Pages always flow right-to-left: the books are Arabic.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 2,
            // No stretch at the first and last page: it draws the pages into an offscreen layer,
            // which a WebView can't be drawn into (the app crashed in the renderer when swiping
            // back from the first page).
            overscrollEffect = null,
            modifier = Modifier
                .fillMaxSize()
                // The turning page swings toward the reader; keep it inside the page area.
                .clipToBounds()
                .pointerInput(Unit) {
                    detectPinch(
                        onStart = {
                            pinchStartSize = ReaderSettingsStore.settings.value.fontSize
                            pinchSize = pinchStartSize
                        },
                        onZoom = { zoom ->
                            val size = (pinchStartSize * zoom).roundToInt()
                                .coerceIn(ReaderSettings.FONT_SIZE_RANGE)
                            if (size != ReaderSettingsStore.settings.value.fontSize) {
                                ReaderSettingsStore.update(context) { it.copy(fontSize = size) }
                            }
                            pinchSize = size
                        },
                        onEnd = { pinchSize = null },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        viewModel.onEvent(BookEvent.ToggleAppBarsVisibility)
                    })
                },
            key = { index -> publication.readingOrder[index].href ?: index.toString() }
        ) { pageIndex ->
            Box(
                Modifier.pageTurn(
                    pagerState = pagerState,
                    page = pageIndex,
                    enabled = readerSettings.pageTurn,
                    background = Color(backgroundColor),
                )
            ) {
            BookPage(
                index = pageIndex,
                topPadding = { pageTop },
                bottomPadding = { pageBottom },
                webViews = cachedWebViews,
                state = state,
                publication = publication,
                backgroundColor = backgroundColor,
                // Only the page holding the search result highlights and scrolls to it.
                javascriptCall = if (publication.readingOrder[pageIndex].href == searchResult.first) searchResult.second else "",
                onTapped = { viewModel.onEvent(BookEvent.ToggleAppBarsVisibility) },
                onRequestMeaning = { word, passage ->
                    viewModel.onEvent(BookEvent.ShowMeaningSheet(word, passage, context))
                },
                saveWebView = { index, webview -> cachedWebViews.put(index, webview) },
                highlightsJs = remember(highlights, pageIndex) {
                    HighlightStore.applyJs(publication.readingOrder[pageIndex].href)
                },
                restoreFraction = savedPosition
                    ?.takeIf { it.first == publication.readingOrder[pageIndex].href }?.second,
                isCurrent = positionReady && pagerState.currentPage == pageIndex,
                // While the book opens, the reader's single loading screen covers the page.
                onShown = onFirstPageShown,
                onScrollFraction = { fraction ->
                    publication.readingOrder[pageIndex].href?.let {
                        ReadingPositionStore.save(context, bookKey, it, fraction)
                    }
                },
                onCreateHighlight = { text, before ->
                    val highlight = Highlight(
                        href = publication.readingOrder[pageIndex].href ?: "",
                        chapterIndex = pageIndex,
                        text = text,
                        before = before,
                    )
                    HighlightStore.add(highlight)
                    editingHighlightId = highlight.id
                },
                onHighlightClicked = { id -> editingHighlightId = id },
            )
            }
        }
        }
        // Current size while pinching.
        androidx.compose.animation.AnimatedVisibility(
            visible = pinchSize != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(ShamelaIcons.FormatSize, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = (pinchSize ?: readerSettings.fontSize).toString(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
        }

        ReaderBarColors(pageTheme) {
        BookTopBar(
            title = publication.metadata.title,
            chapterTitle = chapterTitles.getOrNull(pagerState.currentPage),
            isVisible = state.isAppBarsVisible,
            onBack = navigateBack,
            onSearch = navigateToSearchScreen,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { if (it.height > 0) topBarPx = it.height },
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarsPx = it.height }
                .then(if (state.isAppBarsVisible) Modifier else Modifier.navigationBarsPadding())
        ) {
        // Previous / next search match, shown while reading a search result.
        androidx.compose.animation.AnimatedVisibility(visible = searchSession.isActive) {
            SearchNavigatorBar(
                state = searchSession,
                onPrevious = { SearchSession.step(-1)?.let(onSearchLocator) },
                onNext = { SearchSession.step(1)?.let(onSearchLocator) },
                onBackToResults = navigateToSearchScreen,
                onClose = { SearchSession.close() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        BottomBar(
            visibility = state.isAppBarsVisible,
            currentChapter = pagerState.currentPage,
            chapterCount = publication.readingOrder.size,
            onSeek = { page -> scope.launch { pagerState.scrollToPage(page) } },
            onPrev = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
            onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            onToc = { navigateToTableOfContent(pagerState.currentPage) },
            onSettings = { showReaderSettings = true },
            onHighlights = { showHighlights = true },
        )
        }
        }
    }

    editingHighlightId?.let { id ->
        HighlightStore.get(id)?.let { highlight ->
            HighlightEditSheet(
                highlight = highlight,
                onColor = { color -> HighlightStore.update(id) { it.copy(color = color) } },
                onSaveNote = { note -> HighlightStore.update(id) { it.copy(note = note.trim()) } },
                onDelete = {
                    HighlightStore.remove(id)
                    editingHighlightId = null
                },
                onDismiss = { editingHighlightId = null },
            )
        } ?: run { editingHighlightId = null }
    }

    if (showHighlights) {
        HighlightsListSheet(
            highlights = highlights,
            chapterTitles = chapterTitles,
            onOpen = { h ->
                showHighlights = false
                // A unique call each time, so jumping to the same highlight twice still scrolls.
                onJump(h.href, "/*${System.nanoTime()}*/scrollToUserHighlight('${h.id}')")
            },
            onEdit = { h ->
                showHighlights = false
                editingHighlightId = h.id
            },
            onDismiss = { showHighlights = false },
        )
    }

    if (showReaderSettings) {
        ReaderSettingsSheet(
            settings = readerSettings,
            isAppDark = isAppDark,
            onChange = { transform -> ReaderSettingsStore.update(context, transform) },
            onDismiss = { showReaderSettings = false },
        )
    }


    if (state.showMeaningSheet) {
        MeaningBottomSheet(
            selection = state.meaningQuery,
            messages = state.meaningMessages,
            loading = state.meaningLoading,
            error = state.meaningError,
            onAsk = { viewModel.onEvent(BookEvent.AskMeaningQuestion(it)) },
            onRetry = { viewModel.onEvent(BookEvent.RetryMeaning) },
            onDismiss = { viewModel.onEvent(BookEvent.DismissMeaningSheet) }
        )
    }
}


/**
 * Reader bottom bar: chapter progress ("Chapter X of N") with a scrubber and previous/next
 * chapter buttons, plus quick access to the table of contents and the reading settings.
 */
@Composable
private fun BottomBar(
    visibility: Boolean,
    currentChapter: Int,
    chapterCount: Int,
    onSeek: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToc: () -> Unit,
    onSettings: () -> Unit,
    onHighlights: () -> Unit,
) {
    AnimatedVisibility(
        visible = visibility,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Scrubbing shows the target chapter live; the pager only moves when released.
            var dragValue by remember { mutableStateOf<Float?>(null) }
            val shown = (dragValue?.roundToInt() ?: currentChapter).coerceIn(0, (chapterCount - 1).coerceAtLeast(0))
            Text(
                text = stringResource(R.string.chapter_progress, shown + 1, chapterCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
            // Same direction as the pages, so dragging matches swiping.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, enabled = currentChapter > 0) {
                    Icon(ShamelaIcons.KeyboardArrowRight, contentDescription = stringResource(R.string.previous_chapter))
                }
                Slider(
                    value = dragValue ?: currentChapter.toFloat(),
                    onValueChange = { dragValue = it },
                    onValueChangeFinished = {
                        dragValue?.let { onSeek(it.roundToInt()) }
                        dragValue = null
                    },
                    valueRange = 0f..(chapterCount - 1).coerceAtLeast(1).toFloat(),
                    enabled = chapterCount > 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNext, enabled = currentChapter < chapterCount - 1) {
                    Icon(ShamelaIcons.KeyboardArrowLeft, contentDescription = stringResource(R.string.next_chapter))
                }
            }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BarAction(ShamelaIcons.FormatListBulleted, stringResource(R.string.reader_index), onToc)
                BarAction(ShamelaIcons.BorderColor, stringResource(R.string.highlights_short), onHighlights)
                BarAction(ShamelaIcons.TextFields, stringResource(R.string.reading_settings), onSettings)
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** Reader top bar: book title with the current chapter as subtitle, and search. Reading settings
 *  live in the bottom bar only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookTopBar(
    title: String,
    chapterTitle: String?,
    isVisible: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(250)),
        modifier = modifier,
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = title,
                        style = AppFonts.content(MaterialTheme.typography.titleMedium, naturalAlign = true),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!chapterTitle.isNullOrBlank()) {
                        Text(
                            text = chapterTitle,
                            style = AppFonts.content(MaterialTheme.typography.bodySmall, naturalAlign = true),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(ShamelaIcons.NavigateBack, contentDescription = stringResource(com.shamela.apptheme.R.string.back))
                }
            },
            actions = {
                IconButton(onClick = onSearch) {
                    Icon(ShamelaIcons.Search, contentDescription = stringResource(R.string.menu_item_search))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )
    }
}

/**
 * Chapter title for each reading-order item: the matching table-of-contents entry, or the closest
 * one before it (chapters without their own entry belong to the previous heading).
 */
/**
 * Book-like page turn for the chapter pager. Pages stay in place instead of sliding: the page being
 * left lifts off and rotates around the spine (the right edge, since the books are Arabic and pages
 * flow right-to-left), revealing the next page underneath, with shading on both. Swiping back plays
 * it in reverse. Also used by the app's PDF reader, so both readers turn pages alike.
 */
fun Modifier.pageTurn(
    pagerState: androidx.compose.foundation.pager.PagerState,
    page: Int,
    enabled: Boolean,
    background: Color,
): Modifier {
    if (!enabled) return this.fillMaxSize()
    // > 0: the page being turned away (or turned back); < 0: the page revealed underneath.
    fun offset() = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
    return this
        .fillMaxSize()
        // Turning page on top, then the current page, then the one underneath.
        .zIndex(offset().let { if (it > 0f) 2f else 1f + it.coerceAtLeast(-1f) })
        .graphicsLayer {
            // Only while the pages are being swiped. A jump (chapter slider, search result,
            // table of contents) must not move the page: right after it the pager reports the
            // new page before it has laid the pages out again, which shifted the page sideways
            // for a frame and showed empty strips at its edges.
            if (!pagerState.isScrollInProgress) return@graphicsLayer
            val o = offset()
            // Pages a full width or more away stay where the pager puts them (off screen).
            if (o <= -1f || o >= 1f) return@graphicsLayer
            // The pager lays pages out right-to-left; pin the two visible pages in place.
            translationX = -o * size.width
            if (o > 0f) {
                transformOrigin = TransformOrigin(1f, 0.5f)
                cameraDistance = 14f * density
                rotationY = o * 100f
                alpha = if (o > 0.9f) (1f - o) * 10f else 1f
                // Fade without an offscreen buffer, which the page's WebView can't be drawn into.
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            }
        }
        .drawWithContent {
            val o = offset()
            drawRect(background)
            drawContent()
            when {
                !pagerState.isScrollInProgress -> Unit
                o >= 1f || o <= -1f -> Unit
                // The lifting page darkens as it turns away from the light.
                o > 0f -> drawRect(Color.Black, alpha = o * 0.35f)
                // The page underneath is in the turning page's shadow until uncovered.
                o < 0f -> drawRect(Color.Black, alpha = -o * 0.25f)
            }
        }
}

/**
 * Two-finger pinch, observed before the pages and the WebView get the touches (initial pass) and
 * consumed so the pinch neither swipes chapters nor scrolls. [onZoom] gets the total zoom since
 * the fingers went down. One-finger gestures pass through untouched.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectPinch(
    onStart: () -> Unit,
    onZoom: (Float) -> Unit,
    onEnd: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoom = 1f
        var pinching = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                if (!pinching) {
                    pinching = true
                    onStart()
                }
                zoom *= event.calculateZoom()
                onZoom(zoom)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (pinching) onEnd()
    }
}

internal fun chapterTitles(publication: Publication): List<String?> {
    fun key(href: String?) = href?.substringBefore('#')?.removePrefix("/")
    val tocByHref = LinkedHashMap<String, String>()
    fun walk(links: List<org.readium.r2.shared.Link>) {
        links.forEach { link ->
            val k = key(link.href)
            val t = link.title
            if (k != null && !t.isNullOrBlank() && k !in tocByHref) tocByHref[k] = t
            walk(link.children)
        }
    }
    walk(publication.tableOfContents)
    var last: String? = null
    return publication.readingOrder.map { item ->
        tocByHref[key(item.href)]?.let { last = it }
        last
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BookPage(
    index: Int,
    /** Space kept free above and below the text (system bars, or the reader's bars when shown). */
    topPadding: () -> androidx.compose.ui.unit.Dp,
    bottomPadding: () -> androidx.compose.ui.unit.Dp,
    webViews: Map<Int, WebView>,
    state: BookState,
    publication: Publication,
    backgroundColor: Long,
    javascriptCall: String,
    onTapped: () -> Unit,
    onRequestMeaning: (word: String, context: String) -> Unit,
    saveWebView: (Int, WebView) -> Unit,
    highlightsJs: String,
    restoreFraction: Float?,
    isCurrent: Boolean,
    /** Called once this page is drawn while it is the page on screen. */
    onShown: () -> Unit,
    onScrollFraction: (Float) -> Unit,
    onCreateHighlight: (text: String, before: String) -> Unit,
    onHighlightClicked: (String) -> Unit,
) {
    val lastAppliedJs = remember { mutableStateOf("") }
    // Start as loaded when we already have a pre-rendered cached WebView for this index.
    val pageLoaded = remember { mutableStateOf(webViews.containsKey(index)) }
    val scrollState = rememberScrollState()
    val pageWebView = remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(pageLoaded.value, isCurrent) {
        if (pageLoaded.value && isCurrent) onShown()
    }

    // Draw this chapter's highlights (again whenever they change).
    LaunchedEffect(highlightsJs, pageLoaded.value) {
        if (!pageLoaded.value) return@LaunchedEffect
        val webview = snapshotFlow { pageWebView.value }.first { it != null } ?: return@LaunchedEffect
        webview.evaluateJavascript(highlightsJs, null)
    }

    // Return to where reading stopped in this chapter, once it is laid out.
    var restored by remember { mutableStateOf(restoreFraction == null) }
    LaunchedEffect(restoreFraction, pageLoaded.value) {
        if (restored || restoreFraction == null || !pageLoaded.value) return@LaunchedEffect
        withTimeoutOrNull(5000) {
            snapshotFlow { scrollState.maxValue }.first { it > 0 }
        }
        // Let the reader stylesheet settle (it changes the text height).
        kotlinx.coroutines.delay(300)
        scrollState.scrollTo((restoreFraction * scrollState.maxValue).toInt())
        restored = true
    }

    // Remember how far down the chapter the reader is (only for the chapter on screen).
    LaunchedEffect(isCurrent, restored) {
        if (!isCurrent || !restored) return@LaunchedEffect
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .collect { (value, max) ->
                kotlinx.coroutines.delay(400)
                onScrollFraction(if (max > 0) value.toFloat() / max else 0f)
            }
    }

    // Highlight a search match and scroll to it. The page scrolls in Compose (the WebView is laid
    // out at its full content height), so wait until it is laid out, then center the match.
    LaunchedEffect(javascriptCall, pageLoaded.value) {
        if (javascriptCall.isBlank() || javascriptCall == lastAppliedJs.value || !pageLoaded.value) return@LaunchedEffect
        val webview = withTimeoutOrNull(5000) {
            snapshotFlow { pageWebView.value to scrollState.maxValue }
                .first { (view, _) -> view != null && view.height > 0 && view.contentHeight > 0 }
                .first
        } ?: return@LaunchedEffect
        lastAppliedJs.value = javascriptCall
        val top = suspendCancellableCoroutine { cont ->
            webview.evaluateJavascript(javascriptCall) { cont.resume(it?.toFloatOrNull() ?: -1f) }
        }
        if (top < 0) return@LaunchedEffect
        // CSS px -> view px: the WebView is as tall as its content.
        val scale = webview.height.toFloat() / webview.contentHeight
        val y = top * scale - webview.rootView.height / 3f
        scrollState.animateScrollTo(y.toInt().coerceIn(0, scrollState.maxValue))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // No stretch at the top/bottom of a page either: it draws the page's WebView into an
        // offscreen layer, which crashes the renderer.
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.LocalOverscrollFactory provides null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Horizontal)
                )
                .padding(top = topPadding(), bottom = bottomPadding())
                .verticalScroll(scrollState)
        ) {
            val pageAlpha by androidx.compose.animation.core.animateFloatAsState(
                if (pageLoaded.value) 1f else 0f, label = "page fade-in"
            )
            AndroidView(modifier = Modifier.graphicsLayer {
                alpha = pageAlpha
                // No offscreen buffer for the fade: a WebView can't be drawn into one.
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            }, factory = { context ->
                webViews[index] ?: run {
                    val loadedState = pageLoaded
                    CustomWebView(
                        context,
                        isNightMode = AppTheme.isDarkTheme(context),
                        currentPageIndex = index,
                        currentPageHref = publication.readingOrder[index].href
                    ).apply {
                        setBackgroundColor(backgroundColor.toInt())
                        settings.javaScriptEnabled = true
                        settings.defaultTextEncodingName = "UTF-8"
                        settings.allowFileAccess = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                // Pages built from cached HTML may carry older settings; restyle now.
                                view.evaluateJavascript(
                                    ReaderSettingsStore.settings.value.applyCssJs(AppTheme.isDarkTheme(view.context)),
                                    null
                                )
                                // A reloaded page (e.g. harakat toggled) loses its highlights.
                                view.evaluateJavascript(HighlightStore.applyJs(publication.readingOrder[index].href), null)
                                // Show the page only once its final layout has been drawn: the first
                                // frames of a new chapter are laid out wider than the screen.
                                view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                                    override fun onComplete(requestId: Long) {
                                        loadedState.value = true
                                    }
                                })
                            }
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                if (!request.isForMainFrame
                                    && request.url.path != null
                                    && request.url.path!!.endsWith("/favicon.ico")
                                ) {
                                    try {
                                        return WebResourceResponse("image/png", null, null)
                                    } catch (e: Exception) {
                                        Log.e(FolioActivity.LOG_TAG, "shouldInterceptRequest failed", e)
                                    }
                                }
                                return null
                            }
                        }
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun isTapped() = onTapped()
                            @JavascriptInterface
                            fun textSelected(text: String) {
                                Log.e("CustomWebView", "textSelected: $text")
                            }
                        }, "CustomWebView")
                        addJavascriptInterface(this, "FolioWebView")
                        // Qualify with `this.` — the BookPage `onRequestMeaning` param
                        // (a val) would otherwise shadow this property and fail to assign.
                        this.onRequestMeaning = onRequestMeaning
                        this.onCreateHighlight = onCreateHighlight
                        this.onHighlightClicked = onHighlightClicked
                    }
                }
            }, update = { webview ->
                (webview as CustomWebView).fullScreenMode.value = !state.isAppBarsVisible
                // Reload whenever this page's HTML changes (e.g. harakat toggled), not only once:
                // the WebView remembers which HTML it shows in its tag.
                val (url, htmlData) = state.pagesMap[index] ?: ("" to "")
                if (url.isNotBlank() && webview.tag != htmlData.hashCode()) {
                    webview.tag = htmlData.hashCode()
                    webview.loadDataWithBaseURL(url, htmlData, state.mimeType, "UTF-8", null)
                    saveWebView(index, webview)
                }
                pageWebView.value = webview
            })
        }

        if (!pageLoaded.value) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


/**
 * Gives the reader's bars (and their sheets' triggers) the page's colors: on a sepia or dark page
 * a light-grey bar looked like an empty white strip above and below the text.
 */
@Composable
fun ReaderBarColors(theme: com.folioreader.reader.PageColors, content: @Composable () -> Unit) {
    val page = Color(theme.background)
    val text = Color(theme.text)
    // A shade apart from the page, so the bar reads as a bar without becoming a separate block.
    val bar = androidx.compose.ui.graphics.lerp(page, if (theme.isDark) Color.White else Color.Black, 0.05f)
    val scheme = MaterialTheme.colorScheme.copy(
        surfaceContainer = bar,
        onSurface = text,
        onSurfaceVariant = text.copy(alpha = 0.75f),
        // Slider's empty track and the search navigator: a quiet tint of the page, not app blue.
        secondaryContainer = androidx.compose.ui.graphics.lerp(bar, text, 0.15f),
        onSecondaryContainer = text,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides text, content = content)
    }
}

/** Status and navigation bar icons that stay readable on the page color. */
@Composable
fun ReaderSystemBarIcons(lightPage: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightPage
            isAppearanceLightNavigationBars = lightPage
        }
    }
}
