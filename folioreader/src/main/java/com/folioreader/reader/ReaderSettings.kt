package com.folioreader.reader

import android.content.Context
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The colors a page is drawn in (ARGB). */
data class PageColors(val background: Long, val text: Long, val heading: Long, val link: Long, val isDark: Boolean)

/**
 * Page colors for reading. [FollowApp] takes the app's palette (its page, text and accent colors,
 * e.g. the old-book palette's parchment and sepia), light or dark with the app theme.
 */
enum class ReaderTheme(val background: Long, val text: Long, val heading: Long, val link: Long) {
    FollowApp(0, 0, 0, 0),
    Light(0xFFFFFFFF, 0xFF1C1B1F, 0xFF000000, 0xFF1565C0),
    Sepia(0xFFF4ECD8, 0xFF5B4636, 0xFF3E2F23, 0xFF8B5A2B),
    Dark(0xFF121212, 0xFFD4D4D4, 0xFFF0F0F0, 0xFF90CAF9),
    Black(0xFF000000, 0xFFC8C8C8, 0xFFE8E8E8, 0xFF90CAF9);

    /** The colors to draw with; [isAppDark] only matters for [FollowApp] when no palette is set. */
    fun resolve(isAppDark: Boolean): PageColors {
        if (this != FollowApp) return PageColors(background, text, heading, link, this == Dark || this == Black)
        val scheme = AppTheme.colorScheme
        val dark = scheme.surface.luminance() < 0.5f
        fun argb(c: androidx.compose.ui.graphics.Color) = c.toArgb().toLong() and 0xFFFFFFFFL
        return PageColors(
            background = argb(scheme.surface),
            text = argb(scheme.onSurface),
            heading = argb(scheme.onSurface),
            link = argb(scheme.primary),
            isDark = dark,
        )
    }
}

/**
 * Reading preferences, independent of the app's own font size and theme (enlarging the book text
 * must not enlarge the app's menus).
 */
data class ReaderSettings(
    /** Base text size in CSS px. */
    val fontSize: Int = 18,
    /** Line height multiplier. */
    val lineHeight: Float = 1.9f,
    /** Horizontal page margin in CSS px. */
    val margin: Int = 20,
    val justify: Boolean = false,
    val theme: ReaderTheme = ReaderTheme.FollowApp,
    /** Stored font key (same keys as the app font setting); null = the app's font. */
    val fontKey: String? = null,
    /** Hide harakat (tashkeel) in the text. Changes the page HTML, so pages are reloaded. */
    val hideHarakat: Boolean = false,
    val keepScreenOn: Boolean = true,
    /** Window brightness 0..1, or null to follow the system. */
    val brightness: Float? = null,
    /** Turn chapters like book pages instead of sliding them. */
    val pageTurn: Boolean = true,
) {
    companion object {
        val FONT_SIZE_RANGE = 12..36
        val LINE_HEIGHTS = listOf(1.5f, 1.9f, 2.3f)
        val MARGINS = listOf(12, 20, 32)
    }

    private fun color(c: Long) = String.format("#%06X", c and 0xFFFFFF)

    /**
     * The CSS that applies these settings. It is injected into every page (and replaced live in
     * already-open pages), after Style.css so it takes precedence over the stylesheet's defaults.
     */
    fun css(isAppDark: Boolean): String {
        val t = theme.resolve(isAppDark)
        val fontCss = fontKey?.let { AppFonts.cssClassOf(it) }?.takeIf { it.isNotEmpty() }
            ?.let { "font-family: '$it', sans-serif !important;" } ?: ""
        return """
            html.reader, html.reader body { max-width: 100% !important; overflow-x: hidden !important; }
            html.reader img, html.reader table { max-width: 100% !important; height: auto; }
            html.reader { font-size: ${fontSize}px !important; background-color: ${color(t.background)} !important; $fontCss }
            html.reader body { padding: 20px ${margin}px 48px ${margin}px !important; color: ${color(t.text)} !important;
                text-align: ${if (justify) "justify" else "right"} !important; }
            html.reader body, html.reader p, html.reader span, html.reader div, html.reader li, html.reader td,
            html.reader blockquote { color: ${color(t.text)} !important; background-color: transparent !important;
                line-height: $lineHeight !important; }
            html.reader p, html.reader div { text-align: ${if (justify) "justify" else "inherit"} !important; }
            html.reader h1, html.reader h2, html.reader h3, html.reader h4, html.reader h5, html.reader h6 {
                color: ${color(t.heading)} !important; }
            html.reader a { color: ${color(t.link)} !important; }
            html.reader hr { border-color: ${color(t.text)}33 !important; }
            ${if (t.isDark) "html.reader img { filter: brightness(0.85); }" else ""}
        """.trimIndent()
    }

    fun backgroundColor(isAppDark: Boolean): Long = theme.resolve(isAppDark).background

    /** JavaScript that (re)applies [css] to an already-loaded page. */
    fun applyCssJs(isAppDark: Boolean): String {
        val escaped = css(isAppDark)
            .replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
        return "(function(){var d=document;var s=d.getElementById('shamela-reader');" +
            "if(!s){s=d.createElement('style');s.id='shamela-reader';(d.head||d.documentElement).appendChild(s);}" +
            "s.textContent='$escaped';d.documentElement.classList.add('reader');" +
            "d.documentElement.classList.remove('nightMode');})()"
    }
}

/** Persisted [ReaderSettings], observable so open pages update as soon as a setting changes. */
object ReaderSettingsStore {
    private const val PREFS = "reader_settings"
    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val defaults = ReaderSettings()
        _settings.value = ReaderSettings(
            fontSize = p.getInt("fontSize", defaults.fontSize),
            lineHeight = p.getFloat("lineHeight", defaults.lineHeight),
            margin = p.getInt("margin", defaults.margin),
            justify = p.getBoolean("justify", defaults.justify),
            theme = runCatching { ReaderTheme.valueOf(p.getString("theme", null)!!) }.getOrDefault(defaults.theme),
            fontKey = p.getString("fontKey", null),
            hideHarakat = p.getBoolean("hideHarakat", defaults.hideHarakat),
            keepScreenOn = p.getBoolean("keepScreenOn", defaults.keepScreenOn),
            brightness = p.getFloat("brightness", -1f).takeIf { it >= 0f },
            pageTurn = p.getBoolean("pageTurn", defaults.pageTurn),
        )
        loaded = true
    }

    fun update(context: Context, transform: (ReaderSettings) -> ReaderSettings) {
        val new = transform(_settings.value)
        _settings.value = new
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("fontSize", new.fontSize)
            .putFloat("lineHeight", new.lineHeight)
            .putInt("margin", new.margin)
            .putBoolean("justify", new.justify)
            .putString("theme", new.theme.name)
            .putString("fontKey", new.fontKey)
            .putBoolean("hideHarakat", new.hideHarakat)
            .putBoolean("keepScreenOn", new.keepScreenOn)
            .putFloat("brightness", new.brightness ?: -1f)
            .putBoolean("pageTurn", new.pageTurn)
            .apply()
    }
}

/** Removes Arabic harakat from the text of an HTML document, leaving markup untouched. */
object Harakat {
    private val marks = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
    private val textBetweenTags = Regex(">([^<]+)<")

    fun strip(html: String): String =
        textBetweenTags.replace(html) { m -> ">" + marks.replace(m.groupValues[1], "") + "<" }
}
