package com.shamela.library.presentation.screens.settings


import com.shamela.library.presentation.utils.LibraryImportWorker
import android.content.Intent
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import android.app.Application
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shamela.apptheme.domain.usecases.userPreferences.UserPreferencesUseCases
import com.shamela.apptheme.presentation.settings.PreferenceSettingsEvent
import com.shamela.apptheme.presentation.settings.PreferenceSettingsState
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.AppTheme
import com.shamela.library.R
import com.shamela.library.ShamelaApp
import com.shamela.library.data.local.backup.LibraryBackup
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.data.local.files.FilesRepoImpl
import com.shamela.library.domain.usecases.books.BooksUseCases
import com.shamela.library.presentation.utils.BookIndexing
import com.shamela.library.presentation.utils.ErrorReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.shamela.library.presentation.utils.BookDeletionManager
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject


@HiltViewModel
class SettingsViewModel @Inject constructor(
    @FilesRepoImpl private val booksUseCases: BooksUseCases,
    private val userPreferencesUseCases: UserPreferencesUseCases,
    private val libraryBackup: LibraryBackup,
    private val app: Application,
) : ViewModel() {
    private val _settingsState = MutableStateFlow<SettingsState>(SettingsState())
    val settingsState = _settingsState.asStateFlow()

    private val _toastsChannel = Channel<ToastMessage>(Channel.BUFFERED)
    val toastsChannel = _toastsChannel.receiveAsFlow()

    private val _preferenceSettings = MutableStateFlow<PreferenceSettingsState>(PreferenceSettingsState())
    val preferenceSettings = _preferenceSettings.asStateFlow()

    init {
        initializeSettingsOptions()
        refreshPreferences()
        observeLibraryImport()
    }

    fun refreshPreferences() {
        userPreferencesUseCases.readUserPreferences().let { userPrefs ->
            _preferenceSettings.update { it.copy(userPrefs = userPrefs) }
            val selectedThemePosition =
                preferenceSettings.value.availableFontSizes.indexOf(userPrefs.fontSize)
            _preferenceSettings.update { it.copy(sliderPosition = selectedThemePosition.toFloat()) }
        }
    }

    private fun initializeSettingsOptions() {
        userPreferencesUseCases.getAvailableFontFamilies().let { fonts ->
            _preferenceSettings.update { it.copy(availableFontFamilies = fonts) }
        }
        userPreferencesUseCases.getAvailableFontSizes().let { sizes ->
            _preferenceSettings.update {
                it.copy(availableFontSizes = sizes.map { v -> v.toInt() }.sorted())
            }
        }
        userPreferencesUseCases.getAvailableThemes().let { themes ->
            _preferenceSettings.update { it.copy(availableThemes = themes) }
        }
        userPreferencesUseCases.getAvailableColorSchemes().let { colors ->
            _preferenceSettings.update { it.copy(availableColorSchemes = colors) }
        }
    }


    private suspend fun copyFileToAppFolder(uri: Uri, bookTitle: String): File? {
        return withContext(Dispatchers.IO) {
            ShamelaApp.externalBooksDirectory.run {
                com.shamela.library.data.local.files.StorageDirs.ensure(this)
            }
            val bookFileName = "${bookTitle.removeSuffix(".epub")}.epub"
            val destinationFile = File(ShamelaApp.externalBooksDirectory, bookFileName)
            if (destinationFile.exists()) {
                _toastsChannel.send(ToastMessage(R.string.the_book_already_exists))
                return@withContext null // File already exists, return null
            }
            try {
                app.applicationContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        val size = inputStream.copyTo(outputStream, bufferSize = 8 * 1024)
                        Log.d("SettingsViewModel", "copied $size bytes")
                    }
                }
                destinationFile
            } catch (e: Exception) {
                ErrorReporter.report("SettingsViewModel", "copying the picked book failed", e)
                if (destinationFile.exists()) {
                    destinationFile.delete() // Clean up partially copied file
                }
                _toastsChannel.send(ToastMessage(R.string.could_not_add_book_to_library))
                null
            }
        }

    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.OnChangeViewType -> {
                _settingsState.update { it.copy(selectedViewType = event.newViewType) }
                if (event.newViewType == SettingsViewType.General) refreshStorage()
            }

            SettingsEvent.RefreshStorage -> refreshStorage()

            is SettingsEvent.ImportLibraryFolder -> importLibraryFolder(event.treeUri)

            SettingsEvent.DeleteAllBooks -> {
                viewModelScope.launch {
                    val books = withContext(Dispatchers.IO) { booksUseCases.booksDao.getDownloadedBooksOnce() }
                    // In the background with the floating progress bar, like single-section deletes.
                    BookDeletionManager.delete(
                        books,
                        deleteBook = { booksUseCases.deleteBook(it) },
                        onFinished = { completed ->
                            // Files that never made it into the database (e.g. interrupted
                            // downloads). Not when stopped: it would delete the books kept too.
                            if (completed) File(ShamelaApp.externalMediaDir, "ShamelaDownloads").listFiles()
                                ?.forEach { it.deleteRecursively() }
                            withContext(Dispatchers.Main) { refreshStorage() }
                        },
                    )
                }
            }

            is SettingsEvent.ExportBackup -> {
                viewModelScope.launch {
                    val message = withContext(Dispatchers.IO) {
                        try {
                            val count = app.contentResolver.openOutputStream(event.uri, "wt")
                                ?.use { libraryBackup.export(it) }
                                ?: return@withContext ToastMessage(R.string.backup_failed)
                            ToastMessage(R.string.backup_exported, listOf(count))
                        } catch (e: Exception) {
                            ErrorReporter.report("SettingsViewModel", "backup export failed", e)
                            ToastMessage(R.string.backup_failed)
                        }
                    }
                    _toastsChannel.send(message)
                }
            }

            is SettingsEvent.ImportBackup -> {
                viewModelScope.launch {
                    val message = withContext(Dispatchers.IO) {
                        try {
                            val result = app.contentResolver.openInputStream(event.uri)
                                ?.use { libraryBackup.import(it) }
                                ?: return@withContext ToastMessage(R.string.backup_failed)
                            ToastMessage(R.string.backup_imported, listOf(result.imported, result.skipped))
                        } catch (e: LibraryBackup.InvalidBackupException) {
                            ToastMessage(R.string.backup_invalid)
                        } catch (e: Exception) {
                            ErrorReporter.report("SettingsViewModel", "backup import failed", e)
                            ToastMessage(R.string.backup_failed)
                        }
                    }
                    _toastsChannel.send(message)
                }
            }

            is SettingsEvent.AddExternalBookToLibrary -> {
                viewModelScope.launch {
                    _settingsState.update { it.copy(isLoading = true) }
                    withContext(Dispatchers.IO) {
                        copyFileToAppFolder(event.bookUri, event.bookTitle)?.let { bookFile ->
                            FilesBooksRepoImpl.parseBook(
                                bookFile,
                                ShamelaApp.EXTERNAL_BOOKS_CATEGORY
                            )?.let { book ->
                                booksUseCases.saveDownloadedBook(book)
                                BookIndexing.enqueue(app.applicationContext, book, replace = true)
                                _toastsChannel.send(ToastMessage(R.string.book_added_successfully))
                            } ?: run {
                                // Don't leave an unreadable file behind in the library folder.
                                bookFile.delete()
                                _toastsChannel.send(ToastMessage(R.string.book_is_not_compatible))
                            }
                        }
                        _settingsState.update {
                            it.copy(
                                isLoading = false,
                                fileUri = null,
                                fileName = null,
                            )
                        }
                    }

                }
            }

            is SettingsEvent.NewFileSelected -> {
                _settingsState.update {
                    it.copy(
                        fileUri = event.fileUri,
                        fileName = getFileNameFromUri(app.applicationContext, event.fileUri),
                    )
                }
            }
        }
    }

    /** Copies the books of a picked folder (e.g. the previous app's library) into this library. */
    private fun importLibraryFolder(treeUri: Uri) {
        if (_settingsState.value.importProgress != null) return
        // Keeps the folder readable for the background job, even if the app is restarted meanwhile.
        runCatching {
            app.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        _settingsState.update { it.copy(importProgress = 0 to 0) }
        LibraryImportWorker.start(app, treeUri)
    }

    /** Shows the background import's progress here, and its result once it ends. */
    private fun observeLibraryImport() {
        var sawRunning = false
        WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(LibraryImportWorker.WORK_NAME)
            .onEach { infos ->
                val info = infos.lastOrNull() ?: return@onEach
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
                        sawRunning = true
                        val done = info.progress.getInt(LibraryImportWorker.KEY_DONE, 0)
                        val total = info.progress.getInt(LibraryImportWorker.KEY_TOTAL, 0)
                        _settingsState.update { it.copy(importProgress = done to total) }
                    }
                    else -> {
                        _settingsState.update { it.copy(importProgress = null) }
                        // Only report an import that ended while this screen was watching it.
                        if (!sawRunning) return@onEach
                        sawRunning = false
                        refreshStorage()
                        if (info.state == WorkInfo.State.SUCCEEDED) {
                            val out = info.outputData
                            val imported = out.getInt(LibraryImportWorker.KEY_IMPORTED, 0)
                            val existing = out.getInt(LibraryImportWorker.KEY_EXISTING, 0)
                            val failed = out.getInt(LibraryImportWorker.KEY_FAILED, 0)
                            _toastsChannel.send(
                                if (out.getInt(LibraryImportWorker.KEY_TOTAL, 0) == 0) ToastMessage(R.string.import_no_books)
                                else ToastMessage(R.string.import_done, listOf(imported, existing, failed))
                            )
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun refreshStorage() {
        viewModelScope.launch {
            val (count, bytes) = withContext(Dispatchers.IO) {
                val books = File(ShamelaApp.externalMediaDir, "ShamelaDownloads")
                    .walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".epub") }
                    .toList()
                books.size to books.sumOf { it.length() }
            }
            _settingsState.update { it.copy(downloadedBooksCount = count, downloadedBytes = bytes) }
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        var fileName = ""
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)

        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameColumnIndex: Int = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameColumnIndex != -1) {
                    fileName = it.getString(displayNameColumnIndex)
                }
            }
        }

        cursor?.close()
        return fileName
    }

    fun onPrefsEvent(event: PreferenceSettingsEvent) {
        when (event) {
            is PreferenceSettingsEvent.OnChangeAppFont -> {
                _preferenceSettings.update { it.copy(userPrefs = event.newPrefs) }
                userPreferencesUseCases.updateUserPreferences(event.newPrefs)
                AppFonts.changeFontFamily(AppFonts.fontFamilyOf(event.newPrefs.fontFamily))
            }

            is PreferenceSettingsEvent.OnChangeAppTheme -> {
                _preferenceSettings.update { it.copy(userPrefs = event.userPrefs) }
                userPreferencesUseCases.updateUserPreferences(event.userPrefs)
                AppTheme.changeColorScheme(event.colorScheme, event.userPrefs.theme)
            }

            is PreferenceSettingsEvent.OnChangeAppFontSize -> {
                _preferenceSettings.update { it.copy(userPrefs = event.newPrefs) }
                userPreferencesUseCases.updateUserPreferences(event.newPrefs)
                AppFonts.changeFontSize(event.newPrefs.fontSize)
            }

            is PreferenceSettingsEvent.OnChangeSliderPosition -> {
                _preferenceSettings.update { it.copy(sliderPosition = event.newPosition) }
            }
        }
    }

}