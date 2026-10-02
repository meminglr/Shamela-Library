package com.shamela.library.presentation

import android.content.Context
import com.shamela.apptheme.presentation.util.AppLocale
import android.Manifest
import android.app.DownloadManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.shamela.library.R
import com.shamela.library.presentation.utils.BooksDownloadManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowInsetsControllerCompat
import com.shamela.apptheme.data.sharedPrefs.SharedPreferencesData
import com.shamela.apptheme.domain.usecases.userPreferences.ReadUserPreferences
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.AppTheme
import com.shamela.apptheme.presentation.util.RequestPermission
import com.shamela.library.presentation.navigation.AboutApp
import com.shamela.library.presentation.screens.HomeHostScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onResume() {
        super.onResume()
        AppFonts.setUiDirection(rtl = AppLocale.layoutDirection(this) == androidx.compose.ui.unit.LayoutDirection.Rtl)
    }

    private val userPreferences = SharedPreferencesData(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppFonts.setUiDirection(rtl = AppLocale.layoutDirection(this) == androidx.compose.ui.unit.LayoutDirection.Rtl)
        handleAboutScreenIntent(intent)

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // Bar icon colors follow the theme (see AppTheme.SystemBarsAppearance).

        ReadUserPreferences(userPreferences).invoke().apply {
            AppFonts.changeFontFamily(AppFonts.fontFamilyOf(fontFamily))
            AppFonts.changeFontSize(fontSize)
            AppTheme.changeColorScheme(
                AppTheme.themeOf(
                    theme,
                    colorSchemeName,
                    AppTheme.isDarkTheme(this@MainActivity),
                    this@MainActivity
                ),
                theme
            )
        }
        setContent {
            AppTheme.ShamelaLibraryTheme {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    RequestPermission(permission = Manifest.permission.POST_NOTIFICATIONS, onGranted = {})
                }
                DownloadErrorToasts()
                CompositionLocalProvider(LocalLayoutDirection provides AppLocale.layoutDirection(this@MainActivity)) {
                       HomeHostScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAboutScreenIntent(intent)
    }

    private fun handleAboutScreenIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ABOUT_SCREEN, false) == true) {
            AboutApp.requestOpen()
        }
    }

    @Composable
    private fun DownloadErrorToasts() {
        LaunchedEffect(Unit) {
            BooksDownloadManager.downloadErrors.collect { error ->
                val message = when (error.reason) {
                    BooksDownloadManager.REASON_NO_LINK ->
                        getString(R.string.download_link_unavailable, error.bookTitle)
                    DownloadManager.ERROR_INSUFFICIENT_SPACE ->
                        getString(R.string.download_failed_no_space, error.bookTitle)
                    else -> getString(R.string.download_failed, error.bookTitle)
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_ABOUT_SCREEN = "OPEN_ABOUT_SCREEN"
    }
}
