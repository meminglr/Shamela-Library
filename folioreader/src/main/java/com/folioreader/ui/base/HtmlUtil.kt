package com.folioreader.ui.base

import com.folioreader.reader.Harakat
import com.folioreader.reader.ReaderSettingsStore

import android.content.Context
import com.folioreader.R

/**
 * @author gautam chibde on 14/6/17.
 */
object HtmlUtil {
    /**
     * Function modifies input html string by adding extra css,js and font information.
     *
     * @param context     Activity Context
     * @param content input html raw data
     * @return modified raw html string
     */
    fun getHtmlContent(
        context: Context,
        content: String,
        fontFamilyCssClass: String,
        isNightMode: Boolean,
        fontSizeCssClass: String,
    ): String {
//        Log.e("HtmlUtil", "getHtmlContent: for content:($content),\n font:($font),\n isNightMode:($isNightMode),\n  fontSize:($fontSize)\n", )
        val cssPath = String.format(
            context.getString(R.string.css_tag), "file:///android_asset/css/Style.css"
        )
        val jsPaths = listOf(
            "file:///android_asset/js/rangefix.js",
            "file:///android_asset/js/Bridge.js"
        )

        val scriptTags = jsPaths.joinToString(separator = "\n") { path ->
            String.format(context.getString(R.string.script_tag), path)
        }

        val methodCallScriptTag = String.format(
            context.getString(R.string.script_tag_method_call),
            "setMediaOverlayStyleColors('#C0ED72','#C0ED72')"
        )

        val metaTag =
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no\" />"

        val settings = ReaderSettingsStore.settings.value
        // Reader settings go right after Style.css so they override its defaults; open pages get the
        // same block replaced live (ReaderSettings.applyCssJs).
        val readerStyle = "<style id=\"shamela-reader\">${settings.css(isNightMode)}</style>"

        val toInject = """
    |$cssPath
    |$readerStyle
    |$scriptTags
    |$methodCallScriptTag
    |$metaTag
    |</head>
""".trimMargin()

        // Colors, size and font now come from the "reader" style block; the app font class is the
        // fallback when no reader-specific font is chosen.
        val cssClasses = listOf("reader", fontFamilyCssClass).joinToString(separator = " ")
        val body = if (settings.hideHarakat) Harakat.strip(content) else content

        return StringBuilder(body).replaceFirst(
            "<html".toRegex(),
            "<html class=\"$cssClasses\" onclick=\"onClickHtml()\""
        ).replace("</head>", toInject)
    }
}