@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.pdf

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folioreader.reader.ReaderSettingsStore
import com.folioreader.ui.activity.folioActivity.book.ReaderBarColors
import com.folioreader.ui.activity.folioActivity.book.ReaderSystemBarIcons
import com.folioreader.ui.activity.folioActivity.book.pageTurn
import com.shamela.apptheme.presentation.theme.AppFonts
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import com.shamela.apptheme.presentation.theme.AppTheme
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.apptheme.presentation.util.AppLocale
import com.shamela.library.R
import com.shamela.library.domain.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads a scanned (PDF) book: one page at a time, turned right to left like an Arabic book,
 * pinch or double-tap to zoom, and the last page read remembered per book. Pages are drawn by
 * Android's [PdfRenderer], so no text is shown or searched (the scans' OCR is unusable).
 */
class PdfReaderActivity : ComponentActivity() {

    private var document: PdfDocument? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val file = File(intent.getStringExtra(EXTRA_PATH) ?: return finish())
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: file.path
        val title = intent.getStringExtra(EXTRA_TITLE) ?: file.nameWithoutExtension
        val opened = try {
            PdfDocument(file)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, R.string.pdf_unreadable, android.widget.Toast.LENGTH_LONG).show()
            return finish()
        }
        document = opened
        val positions = getSharedPreferences(PREFS, MODE_PRIVATE)
        setContent {
            AppTheme.ShamelaLibraryTheme {
                CompositionLocalProvider(LocalLayoutDirection provides AppLocale.layoutDirection(this)) {
                    PdfReader(
                        title = title,
                        document = opened,
                        startPage = positions.getInt(bookId, 0).coerceIn(0, opened.pageCount - 1),
                        onPageShown = { positions.edit().putInt(bookId, it).apply() },
                        onBack = ::finish,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        document?.close()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_BOOK_ID = "bookId"
        private const val EXTRA_TITLE = "title"
        private const val PREFS = "pdf_positions"

        fun intent(context: Context, file: File, book: Book): Intent =
            Intent(context, PdfReaderActivity::class.java)
                .putExtra(EXTRA_PATH, file.path)
                .putExtra(EXTRA_BOOK_ID, book.id)
                .putExtra(EXTRA_TITLE, book.title)
    }
}

/** A PDF open for drawing; [PdfRenderer] draws one page at a time, so drawing is serialized. */
private class PdfDocument(file: File) {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    private val lock = Mutex()
    private val cache = object : LruCache<Int, Bitmap>(4) {}
    val pageCount: Int = renderer.pageCount

    /** Page [index] drawn [width] pixels wide (white background, as scans are printed). */
    suspend fun render(index: Int, width: Int): Bitmap? = cache.get(index)?.takeIf { it.width == width }
        ?: withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    renderer.openPage(index).use { page ->
                        val height = (width.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }?.also { cache.put(index, it) }

    fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

/**
 * The reader screen, laid out like the EPUB reader's (BookScreen): the page full screen in the
 * reading theme's color, bars over it that a tap shows or hides — the title on top, and below the
 * page count with a scrubber between previous/next buttons and "go to page" — and the same page turn.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PdfReader(
    title: String,
    document: PdfDocument,
    startPage: Int,
    onPageShown: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    remember { ReaderSettingsStore.load(context) }
    val readerSettings by ReaderSettingsStore.settings.collectAsStateWithLifecycle()
    val pageTheme = readerSettings.theme.resolve(AppTheme.isDarkTheme(context))
    ReaderSystemBarIcons(lightPage = !pageTheme.isDark)
    val pageColor = Color(pageTheme.background)

    val pager = rememberPagerState(initialPage = startPage) { document.pageCount }
    val scope = rememberCoroutineScope()
    var zoomed by remember { mutableStateOf(false) }
    var barsVisible by remember { mutableStateOf(true) }
    var askPage by remember { mutableStateOf(false) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect(onPageShown)
    }

    Box(Modifier.fillMaxSize().background(pageColor)) {
        // Pages follow each other right to left, like the printed book, whatever the app's language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !zoomed,
                beyondViewportPageCount = 1,
                key = { it },
            ) { index ->
                Box(Modifier.pageTurn(pager, index, readerSettings.pageTurn, pageColor)) {
                    PdfPage(
                        document, index,
                        onTap = { barsVisible = !barsVisible },
                        onZoomChanged = { if (index == pager.currentPage) zoomed = it },
                    )
                }
            }
        }

        ReaderBarColors(pageTheme) {
            AnimatedVisibility(
                visible = barsVisible,
                // Motion scheme springs: the bar settles in with a slight give, leaves quickly without one.
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it },
                exit = slideOutVertically(MaterialTheme.motionScheme.fastEffectsSpec()) { -it },
                modifier = Modifier.align(Alignment.TopCenter),
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
                            Text(
                                text = stringResource(R.string.pdf_subtitle, document.pageCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(ShamelaIcons.NavigateBack, contentDescription = stringResource(com.shamela.apptheme.R.string.back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }
            AnimatedVisibility(
                visible = barsVisible,
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it },
                exit = slideOutVertically(MaterialTheme.motionScheme.fastEffectsSpec()) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                PdfBottomBar(
                    current = pager.currentPage,
                    count = document.pageCount,
                    onSeek = { scope.launch { pager.scrollToPage(it) } },
                    onGoTo = { askPage = true },
                )
            }
        }
    }

    if (askPage) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askPage = false },
            title = { Text(stringResource(R.string.pdf_go_to_page)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value -> text = value.filter { it.isDigit() }.take(5) },
                    label = { Text(stringResource(R.string.pdf_page_range, document.pageCount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toIntOrNull()?.let { page ->
                        scope.launch { pager.scrollToPage((page - 1).coerceIn(0, document.pageCount - 1)) }
                    }
                    askPage = false
                }) { Text(stringResource(R.string.pdf_go)) }
            },
            dismissButton = { TextButton(onClick = { askPage = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Like the EPUB reader's bottom bar, with pages instead of chapters. */
@Composable
private fun PdfBottomBar(current: Int, count: Int, onSeek: (Int) -> Unit, onGoTo: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Scrubbing shows the target page live; the pager only moves when released.
        var dragValue by remember { mutableStateOf<Float?>(null) }
        val shown = (dragValue?.roundToInt() ?: current).coerceIn(0, (count - 1).coerceAtLeast(0))
        Text(
            text = stringResource(R.string.pdf_page_progress, shown + 1, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        // Same direction as the pages, so dragging matches swiping.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSeek(current - 1) }, enabled = current > 0) {
                    Icon(ShamelaIcons.KeyboardArrowRight, contentDescription = stringResource(R.string.pdf_previous_page))
                }
                Slider(
                    value = dragValue ?: current.toFloat(),
                    onValueChange = { dragValue = it },
                    onValueChangeFinished = {
                        dragValue?.let { onSeek(it.roundToInt()) }
                        dragValue = null
                    },
                    valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(),
                    enabled = count > 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onSeek(current + 1) }, enabled = current < count - 1) {
                    Icon(ShamelaIcons.KeyboardArrowLeft, contentDescription = stringResource(R.string.pdf_next_page))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onGoTo) {
                Icon(ShamelaIcons.FormatListBulleted, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pdf_go_to_page))
            }
        }
    }
}

@Composable
private fun PdfPage(document: PdfDocument, index: Int, onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalConfiguration.current.screenWidthDp * density.density }
    // Drawn sharper than the screen, so a zoomed page stays readable.
    val width = (screenWidth * 1.5f).toInt().coerceIn(600, 2000)
    val bitmap by produceState<Bitmap?>(null, index, width) { value = document.render(index, width) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            // Clear of the status and navigation bars, like the EPUB reader's text with its bars hidden.
            .windowInsetsPadding(WindowInsets.systemBars)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = (Offset(size.width / 2f, size.height / 2f) - tap) * (scale - 1f)
                        }
                        onZoomChanged(scale > 1f)
                    },
                )
            }
            .pointerInput(Unit) {
                // Pinch to zoom; while zoomed, one finger moves the page. A one-finger swipe on an
                // unzoomed page is left alone, for the pager to turn the page.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            val pan = event.calculatePan()
                            val maxX = size.width * (scale - 1f) / 2f
                            val maxY = size.height * (scale - 1f) / 2f
                            offset = if (scale == 1f) Offset.Zero
                            else Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                            onZoomChanged(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val page = bitmap
        if (page == null) {
            CircularProgressIndicator()
        } else {
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
    }
}
