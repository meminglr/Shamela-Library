package com.shamela.apptheme.presentation.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp


object AppFonts {

    private const val DEFAULT = "خط النظام"
    private const val AMIRI = "خط أميري"
    private const val KITAB = "خط كِتاب"
    private const val TAJAWAL = "خط تَجَوَّل"
    private const val KUFI = "خط كوفي"
    private const val MESSIRI = "خط المسيري"
    private const val CAIRO = "خط كايرو"
    private const val IBM_PLEX = "خط آي بي إم"
    private const val NOTO_NASKH = "خط نوتو نسخ"
    private const val SCHEHERAZADE = "خط شهرزاد"

    /**
     * Whether the app chrome is laid out right-to-left (Arabic UI) or left-to-right (Turkish UI).
     * Set by each activity (see [setUiDirection]); drives text alignment and the UI font.
     */
    private val isRtlUi = mutableStateOf(true)

    /** Called from every activity's onCreate/onResume with its effective layout direction. */
    fun setUiDirection(rtl: Boolean) {
        isRtlUi.value = rtl
    }

    /**
     * Font for app chrome. The user's font is an Arabic typeface: in the Arabic UI it is used for
     * everything; in the Turkish UI Latin labels use the system font (most of the bundled Arabic
     * fonts have no or serif-only Latin glyphs), while Arabic content keeps the user's font via
     * [content].
     */
    private val uiFontFamily by derivedStateOf {
        if (isRtlUi.value) selectedFontFamily.value else FontFamily.Default
    }

    /**
     * Applied to every text style: bidi direction follows the text itself (so "48 سؤالاً" keeps its
     * number in place inside a left-to-right screen), while alignment follows the screen direction
     * so mixed Arabic/Turkish lists stay aligned on one edge.
     */
    private fun TextStyle.directional(): TextStyle = copy(
        textDirection = TextDirection.Content,
        textAlign = if (isRtlUi.value) TextAlign.Right else TextAlign.Left,
    )

    private fun TextStyle.scaled(family: FontFamily): TextStyle {
        val delta = selectedFontSize.value
        return copy(
            fontFamily = family,
            fontSize = (fontSize.value + delta).sp,
            lineHeight = if (lineHeight.isSp) (lineHeight.value + delta).sp else lineHeight,
        ).directional()
    }

    /** The full Material 3 type scale in the selected font and size, used by MaterialTheme. */
    val Typography by derivedStateOf {
        val base = Typography()
        val f = uiFontFamily
        Typography(
            displayLarge = base.displayLarge.scaled(f),
            displayMedium = base.displayMedium.scaled(f),
            displaySmall = base.displaySmall.scaled(f),
            headlineLarge = base.headlineLarge.scaled(f),
            headlineMedium = base.headlineMedium.scaled(f),
            headlineSmall = base.headlineSmall.scaled(f),
            titleLarge = base.titleLarge.scaled(f),
            titleMedium = base.titleMedium.scaled(f).copy(fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.scaled(f),
            bodyLarge = base.bodyLarge.scaled(f),
            bodyMedium = base.bodyMedium.scaled(f),
            bodySmall = base.bodySmall.scaled(f),
            labelLarge = base.labelLarge.scaled(f),
            labelMedium = base.labelMedium.scaled(f),
            labelSmall = base.labelSmall.scaled(f),
        )
    }

    /**
     * For Arabic content (book titles, authors, quotes, descriptions): always the user's Arabic
     * font, a touch larger because Arabic glyphs read smaller than Latin at the same size.
     */
    fun content(style: TextStyle, naturalAlign: Boolean = false): TextStyle = style.copy(
        fontFamily = selectedFontFamily.value,
        fontSize = (style.fontSize.value + if (isRtlUi.value) 0 else 1).sp,
    ).directional().let {
        // Single-line, ellipsized Arabic text must align to its own start: forcing left alignment
        // on right-to-left text makes Android clip the first letters instead of ellipsizing.
        if (naturalAlign) it.copy(textAlign = TextAlign.Start) else it
    }

    private lateinit var AmiriFamily: FontFamily
    private lateinit var KitabFamily: FontFamily
    private lateinit var TajawalFamily: FontFamily
    private lateinit var MessiriFamily: FontFamily
    private lateinit var KufiFamily: FontFamily
    private lateinit var CairoFamily: FontFamily
    private lateinit var IbmPlexFamily: FontFamily
    private lateinit var NotoNaskhFamily: FontFamily
    private lateinit var ScheherazadeFamily: FontFamily

    private val availableFonts = mutableMapOf<String, Pair<FontFamily, Typeface?>>()

    fun init(context: Context) {
        val assets = context.assets
        fun typeface(file: String) = Typeface.createFromAsset(assets, "fonts/$file")

        val amiriTypeface = typeface("amiri_regular.ttf")
        val kitabTypeface = typeface("kitab_regular.ttf")
        val tajawalTypeface = typeface("tajawal_regular.ttf")
        val messiriTypeface = typeface("messiri_regular.ttf")
        val kufiTypeface = typeface("kufi_regular.ttf")
        val cairoTypeface = typeface("cairo_regular.ttf")
        val ibmPlexTypeface = typeface("ibm_plex_arabic_regular.ttf")
        val notoNaskhTypeface = typeface("noto_naskh_arabic_regular.ttf")
        val scheherazadeTypeface = typeface("scheherazade_regular.ttf")

        AmiriFamily = FontFamily(amiriTypeface)
        KitabFamily = FontFamily(kitabTypeface)
        TajawalFamily = FontFamily(tajawalTypeface)
        MessiriFamily = FontFamily(messiriTypeface)
        KufiFamily = FontFamily(kufiTypeface)
        CairoFamily = FontFamily(cairoTypeface)
        IbmPlexFamily = FontFamily(ibmPlexTypeface)
        NotoNaskhFamily = FontFamily(notoNaskhTypeface)
        ScheherazadeFamily = FontFamily(scheherazadeTypeface)

        availableFonts.clear()
        availableFonts[DEFAULT] = Pair(FontFamily.Default, null)
        availableFonts[AMIRI] = Pair(AmiriFamily, amiriTypeface)
        availableFonts[KITAB] = Pair(KitabFamily, kitabTypeface)
        availableFonts[TAJAWAL] = Pair(TajawalFamily, tajawalTypeface)
        availableFonts[MESSIRI] = Pair(MessiriFamily, messiriTypeface)
        availableFonts[KUFI] = Pair(KufiFamily, kufiTypeface)
        availableFonts[CAIRO] = Pair(CairoFamily, cairoTypeface)
        availableFonts[IBM_PLEX] = Pair(IbmPlexFamily, ibmPlexTypeface)
        availableFonts[NOTO_NASKH] = Pair(NotoNaskhFamily, notoNaskhTypeface)
        availableFonts[SCHEHERAZADE] = Pair(ScheherazadeFamily, scheherazadeTypeface)

        selectedFontFamily.value = TajawalFamily
    }

    fun getAvailableFontFamilies(): Set<String> {
        return availableFonts.keys.sorted().toSet()
    }

    fun getAvailableFontSizes() = setOf("4", "2", "0", "-2", "-4")

    fun fontFamilyOf(font: String): FontFamily {
        return availableFonts[font]?.first ?: availableFonts[NOTO_NASKH]?.first ?: FontFamily.Default
    }

    private val selectedFontFamily = mutableStateOf<FontFamily>(FontFamily.Default)
    private val selectedFontSize = mutableStateOf(0)

    fun changeFontFamily(newFontFamily: FontFamily) {
        selectedFontFamily.value = newFontFamily
    }

    fun changeFontSize(newFontSize: Int) {
        selectedFontSize.value = newFontSize
    }

    val textSmall by derivedStateOf {
        TextStyle(
            fontFamily = uiFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (selectedFontSize.value + 12).sp
        ).directional()
    }
    val textSmallBold by derivedStateOf {
        TextStyle(
            fontFamily = uiFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (selectedFontSize.value + 12).sp
        ).directional()
    }

    val textNormal by derivedStateOf {
        TextStyle(
            fontFamily = uiFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (selectedFontSize.value + 16).sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Bottom, trim = LineHeightStyle.Trim.Both)
        ).directional()
    }

    val textNormalBold by derivedStateOf {
        TextStyle(
            fontFamily = uiFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (selectedFontSize.value + 16).sp
        ).directional()
    }

    val textLarge by derivedStateOf {
        TextStyle(
            fontFamily = uiFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (selectedFontSize.value + 20).sp
        ).directional()
    }

    val textLargeBold by derivedStateOf {
        TextStyle(
            fontFamily = uiFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (selectedFontSize.value + 20).sp
        ).directional()
    }

    fun selectedFontTypeFace(context: Context): Typeface? {
        return availableFonts.entries.find { it.value.first == selectedFontFamily.value }?.value?.second
    }

    fun selectedFontFamilyCssClass(): String {
        return when (selectedFontFamily.value) {
            AmiriFamily -> "amiri"
            KitabFamily -> "kitab"
            KufiFamily -> "kufi"
            MessiriFamily -> "messiri"
            TajawalFamily -> "tajawal"
            CairoFamily -> "cairo"
            IbmPlexFamily -> "ibm_plex"
            NotoNaskhFamily -> "noto_naskh"
            ScheherazadeFamily -> "scheherazade"
            else -> ""
        }
    }

    fun selectedFontSizeCssClass(): String {
        val fontSizeClasses =
            listOf("textSizeOne", "textSizeTwo", "textSizeThree", "textSizeFour", "textSizeFive")
        val availableFontSizes = getAvailableFontSizes().map { it.toInt() }.sorted()
        val fontSizeClassMap = availableFontSizes.zip(fontSizeClasses).toMap()
        return fontSizeClassMap[selectedFontSize.value] ?: "textSizeTwo"
    }
}
