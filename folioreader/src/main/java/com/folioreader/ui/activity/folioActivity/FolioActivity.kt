package com.folioreader.ui.activity.folioActivity

import com.folioreader.reader.SearchSession
import org.json.JSONObject
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.util.AppLocale
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.folioreader.reader.ReaderSettingsStore
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.folioreader.Constants.BOOK_TITLE
import com.folioreader.Constants.CHAPTER_SELECTED
import com.folioreader.Constants.EPUB_FILE_PATH
import com.folioreader.Constants.SETTINGS_CHANGED
import com.folioreader.FolioReader
import com.folioreader.model.locators.SearchLocator
import com.folioreader.ui.activity.ContentHighlightActivity
import com.folioreader.ui.activity.folioActivity.book.BookScreen
import com.folioreader.ui.activity.searchActivity.SearchActivity
import com.shamela.apptheme.presentation.common.LoadingScreen
import com.shamela.apptheme.presentation.theme.AppTheme


class FolioActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private val viewModel: FolioActivityViewModel by viewModels()

    companion object {
        const val LOG_TAG = "FolioActivityCompose"
        const val INTENT_EPUB_SOURCE_PATH = "com.folioreader.epub_asset_path"
        const val EXTRA_READ_LOCATOR = "com.folioreader.extra.READ_LOCATOR"
        const val EXTRA_SEARCH_ITEM = "EXTRA_SEARCH_ITEM"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppFonts.setUiDirection(rtl = AppLocale.layoutDirection(this) == LayoutDirection.Rtl)

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false

        Log.e(LOG_TAG, "-> onCreate")
        val epubFilePath = intent.getStringExtra(INTENT_EPUB_SOURCE_PATH) ?: ""
        val startPageHref = intent.getStringExtra(FolioReader.START_PAGE_HREF) ?:""
        val bookId = intent.getStringExtra(SearchActivity.Book_ID) ?:""
        viewModel.onEvent(FolioActivityEvent.InitializeBook(epubFilePath))
        // Search results belong to one book; opening another one starts without them.
        if (SearchSession.state.value.bookId != bookId) SearchSession.start(bookId, "", emptyList(), -1)
        LocalBroadcastManager.getInstance(this).registerReceiver(
            closeBroadcastReceiver,
            IntentFilter(FolioReader.ACTION_CLOSE_FOLIOREADER)
        )
        val searchLocator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SEARCH_ITEM, SearchLocator::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_SEARCH_ITEM)
        }
        Log.e(LOG_TAG, "intent data of searchLocator: ${searchLocator.toString()}")
        searchLocator?.let {
            viewModel.onEvent(
                FolioActivityEvent.OnSearchResult(
                    href = it.href,
                    jsCall = highlightSearchLocator(it)
                )
            )
        }


        setContent {
            val state = viewModel.state.collectAsStateWithLifecycle().value
            AppTheme.ShamelaLibraryTheme {
                // One loading screen while the book opens, in the page's color, kept until the first
                // page is drawn. (Reading the book, preparing the chapters and drawing the page each
                // showed their own indicator before, one after another.)
                var firstPageShown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    // Never leave the book hidden if the page doesn't report back.
                    kotlinx.coroutines.delay(10_000)
                    firstPageShown = true
                }
                val pageColor = remember {
                    androidx.compose.ui.graphics.Color(
                        ReaderSettingsStore.load(this@FolioActivity).let {
                            ReaderSettingsStore.settings.value.backgroundColor(AppTheme.isDarkTheme(this@FolioActivity))
                        }
                    )
                }
                // Bars and sheets follow the UI language; BookScreen keeps the pages (and the chapter
                // scrubber) right-to-left, because page order follows the Arabic book.
                CompositionLocalProvider(LocalLayoutDirection provides AppLocale.layoutDirection(this@FolioActivity)) {
                    state.publication?.let { publication ->
                        val searchResult = viewModel.searchResult.collectAsStateWithLifecycle().value
                        val selectedChapter = viewModel.selectedChapter.collectAsStateWithLifecycle().value
                        val settingsChanged = viewModel.settingsChanged.collectAsStateWithLifecycle().value


                        BookScreen(
                            navigateToTableOfContent = { selectedPage ->
                                onTableOfContentSelected(
                                    publication.readingOrder[selectedPage].href ?: "",
                                    publication.metadata.title,
                                    epubFilePath
                                )
                            },
                            navigateToSettings = { selectedPage ->
                                onSettingsSelected(
                                    publication.readingOrder[selectedPage].href ?: "",
                                    publication.metadata.title,
                                    epubFilePath
                                )
                            },
                            navigateToSearchScreen = {
                                onSearchButtonClick(bookId,epubFilePath)
                            },
                            publication = publication,
                            navigateBack = { finish() },
                            streamUrl = viewModel.streamUrl,
                            searchResult = searchResult,
                            selectedChapter = selectedChapter,
                            settingsChanged = settingsChanged,
                            startPageHref = startPageHref,
                            onSearchLocator = { locator ->
                                viewModel.onEvent(
                                    FolioActivityEvent.OnSearchResult(
                                        href = locator.href,
                                        jsCall = highlightSearchLocator(locator)
                                    )
                                )
                            },
                            onJump = { href, js ->
                                viewModel.onEvent(FolioActivityEvent.OnSearchResult(href = href, jsCall = js))
                            },
                            onFirstPageShown = { firstPageShown = true },
                        )
                    }

                }
                LoadingScreen(visibility = state.isLoading || !firstPageShown, background = pageColor)
            }
        }
    }


    private fun onSearchButtonClick(bookId:String,epubFilePath: String) {
        val intent = Intent(this@FolioActivity, SearchActivity::class.java)
        intent.putExtra(EPUB_FILE_PATH, epubFilePath)
        intent.putExtra(SearchActivity.Book_ID, bookId)
        intent.putExtra(SearchActivity.Search_Type, SearchActivity.Search_Type_SingleBookSearch)
        searchLauncher.launch(intent)
    }

    private fun onTableOfContentSelected(currentHref: String, title: String, epubFilePath: String) {
        val intent = Intent(this@FolioActivity, ContentHighlightActivity::class.java)
        intent.putExtra(CHAPTER_SELECTED, currentHref)
        intent.putExtra(EPUB_FILE_PATH, epubFilePath)
        intent.putExtra(
            ContentHighlightActivity.SELECTED_VIEW_TYPE,
            ContentHighlightActivity.TableOfContent
        )
        intent.putExtra(BOOK_TITLE, title)
        contentHighlightLauncher.launch(intent)
    }

    private fun onSettingsSelected(currentHref: String, title: String, epubFilePath: String) {
        val intent = Intent(this@FolioActivity, ContentHighlightActivity::class.java)
        intent.putExtra(CHAPTER_SELECTED, currentHref)
        intent.putExtra(EPUB_FILE_PATH, epubFilePath)
        intent.putExtra(
            ContentHighlightActivity.SELECTED_VIEW_TYPE,
            ContentHighlightActivity.Settings
        )
        intent.putExtra(BOOK_TITLE, title)
        contentHighlightLauncher.launch(intent)
    }


    private val searchLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                data?.let {
                    val searchLocator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        data.getParcelableExtra(EXTRA_SEARCH_ITEM, SearchLocator::class.java)
                    } else {
                        data.getParcelableExtra(EXTRA_SEARCH_ITEM)
                    }
                    Log.e(LOG_TAG, "data of searchLauncher: ${searchLocator.toString()}")
                    searchLocator?.let {
                        viewModel.onEvent(
                            FolioActivityEvent.OnSearchResult(
                                href = it.href,
                                jsCall = highlightSearchLocator(it)
                            )
                        )
                    }
                }

            }
        }

    private fun highlightSearchLocator(searchLocator: SearchLocator): String {
        val text = JSONObject.quote(searchLocator.text?.hightlight.orEmpty())
        val before = JSONObject.quote(searchLocator.text?.before.orEmpty())
        // Unique each time, so showing the same match again still scrolls to it.
        return "/*${System.nanoTime()}*/highlightSearchLocator($text, $before)"
    }

    private val contentHighlightLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                data?.let {
                    data.getStringExtra(CHAPTER_SELECTED)?.let { href ->
                        Log.e(LOG_TAG, "ContentHighlight: Navigating to href: $href")
                        viewModel.onEvent(FolioActivityEvent.OnSelectedChapter(href))
                    }
                    val hash = data.getIntExtra(SETTINGS_CHANGED, 0)
                    if (hash != 0) {
                        Log.e(LOG_TAG, "ContentHighlight: SettingsChanged hash=$hash")
                        viewModel.onEvent(FolioActivityEvent.OnSettingsChanged(hash))
                    }
                }
            }
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.v(LOG_TAG, "-> onNewIntent")

        val action = getIntent().action
        if (action != null && action == FolioReader.ACTION_CLOSE_FOLIOREADER) {

            if (!viewModel.state.value.isTopActivity) {
                finish()
                // To determine if app in background or foreground
                val taskImportance = viewModel.state.value.taskImportance
                var appInBackground = false
                if (ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED == taskImportance)
                    appInBackground = true
                if (appInBackground)
                    moveTaskToBack(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppFonts.setUiDirection(rtl = AppLocale.layoutDirection(this) == LayoutDirection.Rtl)
        Log.v(LOG_TAG, "-> onResume")
        viewModel.onEvent(FolioActivityEvent.OnChangeTopActivity(true))
        val action = intent.action
        if (action != null && action == FolioReader.ACTION_CLOSE_FOLIOREADER) {
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        Log.v(LOG_TAG, "-> onStop")
        viewModel.onEvent(FolioActivityEvent.OnChangeTopActivity(false))
    }

    private val closeBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.v(LOG_TAG, "-> closeBroadcastReceiver -> onReceive -> ${intent.action}")

            val action = intent.action
            if (action != null && action == FolioReader.ACTION_CLOSE_FOLIOREADER) {

                try {
                    val activityManager =
                        context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
                    val tasks = activityManager.runningAppProcesses
                    viewModel.onEvent(FolioActivityEvent.OnChangeTaskImportance(tasks[0].importance))
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "-> ", e)
                }

                val closeIntent = Intent(applicationContext, FolioActivity::class.java)
                closeIntent.flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                closeIntent.action = FolioReader.ACTION_CLOSE_FOLIOREADER
                this@FolioActivity.startActivity(closeIntent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.unregisterReceiver(closeBroadcastReceiver)
        viewModel.onEvent(FolioActivityEvent.StopStreamerServer)
        if (isFinishing) {
            localBroadcastManager.sendBroadcast(Intent(FolioReader.ACTION_FOLIOREADER_CLOSED))
            FolioReader.get().retrofit = null
            FolioReader.get().r2StreamerApi = null
        }
    }

}