package com.folioreader.ui.activity

import com.shamela.apptheme.presentation.common.SegmentedTabs
import com.folioreader.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import com.shamela.apptheme.presentation.util.AppLocale
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.folioreader.Constants
import com.folioreader.Constants.CHAPTER_SELECTED
import com.folioreader.Constants.SETTINGS_CHANGED
import com.folioreader.ui.composables.LinkItem
import com.shamela.apptheme.presentation.common.DefaultTopBar
import com.shamela.apptheme.presentation.common.LoadingScreen
import com.shamela.apptheme.presentation.settings.PreferenceSettingsUI
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.Link
import org.readium.r2.streamer.parser.EpubParser

class ContentHighlightActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    val viewmodel : ContentHighlightViewModel by viewModels(factoryProducer = { ContentHighlightViewModel.Factory })
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppFonts.setUiDirection(rtl = AppLocale.layoutDirection(this) == androidx.compose.ui.unit.LayoutDirection.Rtl)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        val bookPath = intent.getStringExtra(Constants.EPUB_FILE_PATH)
        val bookTitle = intent.getStringExtra(Constants.BOOK_TITLE)
        val selectedViewType = when (intent.getStringExtra(SELECTED_VIEW_TYPE)) {
            Settings -> ViewType.Settings
            TableOfContent -> ViewType.TableOfContents
            else -> ViewType.TableOfContents
        }
        val linkItems = mutableStateListOf<Link>()
        val isLoading = mutableStateOf(true)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    bookPath?.let { filepath ->
                        EpubParser().parse(filepath, "")?.let { pubBox ->
                            val list =
                                pubBox.publication.tableOfContents.ifEmpty { pubBox.publication.readingOrder }
                            linkItems.addAll(list)
                            isLoading.value = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ContentHighlightActivity", "parseEpub: ${e.message}")
                }
            }
        }

        setContent {
            AppTheme.ShamelaLibraryTheme {
                val uiState = viewmodel.preferenceSettings.collectAsStateWithLifecycle()
                val currentViewType = rememberSaveable { mutableStateOf(selectedViewType) }

                // When back is pressed on the Settings tab, notify FolioActivity to reload
                if (currentViewType.value == ViewType.Settings) {
                    BackHandler {
                        onSettingsChanged(uiState.value.hashCode())
                    }
                }

                CompositionLocalProvider(LocalLayoutDirection provides AppLocale.layoutDirection(this@ContentHighlightActivity)) {
                    Scaffold(
                        topBar = {
                            Column {
                                DefaultTopBar(
                                    title = bookTitle ?: stringResource(R.string.app_name),
                                    onNavigateBack = {
                                        if (currentViewType.value == ViewType.Settings) {
                                            onSettingsChanged(uiState.value.hashCode())
                                        } else {
                                            finish()
                                        }
                                    }
                                )

                                SegmentedTabs(
                                    options = ViewType.entries,
                                    selected = currentViewType.value,
                                    label = { stringResource(it.label) },
                                    onSelect = { viewType -> currentViewType.value = viewType },
                                )
                            }
                        }
                    ) {
                        when (currentViewType.value) {
                            ViewType.TableOfContents -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(it),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    contentPadding = PaddingValues(vertical = 16.dp)
                                ) {
                                    items(linkItems) { link ->
                                        LinkItem(link, 0, link == linkItems.first(), ::onTocClicked)
                                    }
                                }
                            }

                            ViewType.Settings -> {
                                PreferenceSettingsUI(
                                    modifier = Modifier.padding(it),
                                    onEvent = {viewmodel.onPrefsEvent(it)},
                                    uiState = uiState.value)
                            }
                        }
                    }
                }
                LoadingScreen(isLoading.value)
            }
        }
    }


    override fun onResume() {
        super.onResume()
        AppFonts.setUiDirection(rtl = AppLocale.layoutDirection(this) == androidx.compose.ui.unit.LayoutDirection.Rtl)
        viewmodel.refreshPreferences()
    }

    private fun onTocClicked(title: String?, href: String?) {
        val intent = Intent()
        intent.putExtra(CHAPTER_SELECTED, href)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun onSettingsChanged(hash:Int) {
        val intent = Intent()
        intent.putExtra(SETTINGS_CHANGED, hash)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    companion object {
        const val SELECTED_VIEW_TYPE = "SELECTED_VIEW_TYPE"
        const val Settings = "Settings_View_type"
        const val TableOfContent = "TOC_View_type"
    }
}


private enum class ViewType(@androidx.annotation.StringRes val label: Int) {
    TableOfContents(label = R.string.book_contents),
    Settings(label = R.string.reader_settings)
}
