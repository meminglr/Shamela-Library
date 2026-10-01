package com.shamela.library.presentation.screens.settings


import com.shamela.apptheme.presentation.common.SegmentedTabs
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shamela.apptheme.presentation.common.LoadingScreen
import com.shamela.apptheme.presentation.settings.PreferenceSettingsEvent
import com.shamela.apptheme.presentation.settings.PreferenceSettingsState
import com.shamela.apptheme.presentation.settings.PreferenceSettingsUI
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.AppTheme
import com.shamela.apptheme.presentation.theme.colors.Green
import com.shamela.apptheme.presentation.util.ShamelaPrev
import com.shamela.library.presentation.screens.LocalPaddingValues
import com.shamela.library.presentation.screens.settings.components.ExternalBooksScreen
import com.shamela.library.presentation.screens.settings.components.GeneralSettingsScreen
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState = viewModel.settingsState.collectAsStateWithLifecycle().value
    val preferenceUiState = viewModel.preferenceSettings.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refreshPreferences() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPreferences()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.onEvent(SettingsEvent.NewFileSelected(uri))
            }
        })
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let { viewModel.onEvent(SettingsEvent.ExportBackup(it)) } }
    )
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { viewModel.onEvent(SettingsEvent.ImportBackup(it)) } }
    )
    LaunchedEffect(key1 = Unit) {
        viewModel.toastsChannel.collect { message ->
            val text = context.getString(message.res, *message.args.toTypedArray())
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    SettingsScreenUI(
        uiState = uiState,
        preferenceUiState = preferenceUiState,
        onClickSelectBook = { getContentLauncher.launch("application/epub+zip") },
        onChangeViewType = { viewModel.onEvent(SettingsEvent.OnChangeViewType(it)) },
        onClickAddBookToLibrary = {
            uiState.fileUri?.let { uri ->
                uiState.fileName?.let {
                    viewModel.onEvent(SettingsEvent.AddExternalBookToLibrary(uri, uiState.fileName))
                }
            }
        },
        onPreferenceEvent = { viewModel.onPrefsEvent(it)},
        onDeleteAllBooks = { viewModel.onEvent(SettingsEvent.DeleteAllBooks) },
        onExportBackup = { exportLauncher.launch("shamela-backup.json") },
        onImportBackup = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
    )
}

@Composable
private fun SettingsScreenUI(
    uiState: SettingsState,
    preferenceUiState: PreferenceSettingsState,
    onClickSelectBook: () -> Unit,
    onChangeViewType: (SettingsViewType) -> Unit,
    onClickAddBookToLibrary: () -> Unit,
    onPreferenceEvent: (PreferenceSettingsEvent) -> Unit,
    onDeleteAllBooks: () -> Unit = {},
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
) {
    val localPadding = LocalPaddingValues.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(localPadding)
    ) {
        SegmentedTabs(
            options = SettingsViewType.entries,
            selected = uiState.selectedViewType,
            label = { stringResource(it.label) },
            onSelect = onChangeViewType,
        )
        LoadingScreen(visibility = uiState.isLoading)
        when (uiState.selectedViewType) {
            SettingsViewType.Preferences -> PreferenceSettingsUI(
                uiState = preferenceUiState,
                onEvent = onPreferenceEvent
            )

            SettingsViewType.General -> Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                GeneralSettingsScreen(
                    downloadedBooksCount = uiState.downloadedBooksCount,
                    downloadedBytes = uiState.downloadedBytes,
                    onDeleteAllBooks = onDeleteAllBooks,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup,
                )
            }

            SettingsViewType.ExternalBooks -> Column(Modifier.padding(horizontal = 16.dp)) {
                ExternalBooksScreen(
                onClickSelectBook = onClickSelectBook,
                onClickAddBookToLibrary = onClickAddBookToLibrary,
                selectedFileName = uiState.fileName,
                selectedFileUri = uiState.fileUri
                )
            }

        }
    }
}

@ShamelaPrev
@Composable
private fun SettingScreenPrev_Preferences() {
    AppTheme.ShamelaLibraryTheme {
        AppTheme.changeColorScheme(Green.lightColorScheme, Green.name)
        SettingsScreenUI(
            uiState = SettingsState(selectedViewType = SettingsViewType.Preferences),
            onClickSelectBook = { },
            onChangeViewType = { },
            onClickAddBookToLibrary = {},
            onPreferenceEvent = {},
            preferenceUiState = PreferenceSettingsState(
                availableFontFamilies = listOf("font1", "font2", "font3"),
                availableFontSizes = listOf(12, 14, 16, 18, 20),
                availableThemes = listOf("theme1", "theme2", "theme3"),
            )
        )
    }
}

@ShamelaPrev
@Composable
private fun SettingScreenPrev_ExternalBooks() {
    AppTheme.ShamelaLibraryTheme {
        AppTheme.changeColorScheme(Green.lightColorScheme, Green.name)
        SettingsScreenUI(
            uiState = SettingsState(selectedViewType = SettingsViewType.ExternalBooks),
            onClickSelectBook = { },
            onChangeViewType = { },
            onClickAddBookToLibrary = {},
            onPreferenceEvent = {},
            preferenceUiState = PreferenceSettingsState()
        )
    }
}
