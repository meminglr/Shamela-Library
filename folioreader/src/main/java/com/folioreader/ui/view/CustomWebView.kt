package com.folioreader.ui.view

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ActionMode
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.PopupWindow
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.folioreader.FolioReader
import com.folioreader.R
import com.folioreader.databinding.TextSelectionNewBinding
import com.folioreader.model.DisplayUnit
import com.folioreader.util.UiUtil
import com.shamela.apptheme.presentation.theme.AppFonts
import org.json.JSONObject
import kotlin.math.ceil

class CustomWebView(
    private val context: Context,
    private val isNightMode: Boolean,
    private val currentPageIndex:Int,
    private val currentPageHref:String?
) : WebView(context) {
    private var actionMode: ActionMode? = null
    private var density: Float = 0f
    private var popupWindow = PopupWindow()
    private var selectionRect = Rect()
    private val popupRect = Rect()
    private var handleHeight: Int = 0
    private lateinit var viewTextSelectionNew: View
    private lateinit var uiHandler: Handler
    private val LOG_TAG = "CustomWebView"
    val fullScreenMode = mutableStateOf(false)

    /**
     * Invoked (on the UI thread) when the user taps "المعنى" on a text selection.
     * Set by [com.folioreader.ui.activity.folioActivity.book.BookScreen] so the
     * lookup stays inside the reader's Compose tree (drives the meaning sheet via
     * BookViewModel), rather than leaving the module like the favorite action.
     */
    var onRequestMeaning: ((word: String, context: String) -> Unit)? = null

    /** "Highlight" on a selection: (selected text, text just before it). Called on the UI thread. */
    var onCreateHighlight: ((String, String) -> Unit)? = null

    /** A tap on an existing highlight (its id). Called on the UI thread. */
    var onHighlightClicked: ((String) -> Unit)? = null

    init {
        // Pages only scroll vertically (in Compose); never sideways, not even for a frame while
        // a new chapter is laid out.
        isHorizontalScrollBarEnabled = false
        // The page scrolls in Compose; the WebView's own scrollbar only flashed at its edge.
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        density = resources.displayMetrics.density
        uiHandler = Handler()
        initViewTextSelectionNew()
    }

    private lateinit var binding: TextSelectionNewBinding
    fun initViewTextSelectionNew() {
        Log.v(LOG_TAG, "-> initViewTextSelectionNew")

        val textSelectionMiddleDrawable = ContextCompat.getDrawable(
            context,
            R.drawable.abc_text_select_handle_middle_mtrl_dark
        )
        handleHeight = textSelectionMiddleDrawable?.intrinsicHeight ?: (24 * density).toInt()

        val ctw = if (isNightMode) {
            ContextThemeWrapper(context, R.style.FolioNightTheme)
        } else {
            ContextThemeWrapper(context, R.style.FolioDayTheme)
        }

        val inflater = LayoutInflater.from(ctw)
        binding = TextSelectionNewBinding.inflate(inflater)
        viewTextSelectionNew = binding.root
        viewTextSelectionNew.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        viewTextSelectionNew.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)

        binding.copySelection.setOnClickListener {
            dismissPopupWindow()
            loadUrl("javascript:onTextSelectionItemClicked(${it.id})")
        }
        binding.shareSelection.setOnClickListener {
            dismissPopupWindow()
            loadUrl("javascript:onTextSelectionItemClicked(${it.id})")
        }
        binding.addSelectionToFavirite.setOnClickListener {
            dismissPopupWindow()
            loadUrl("javascript:onTextSelectionItemClicked(${it.id})")
            //TODO:) this code must be changed
        }
        binding.highlightSelection.setOnClickListener {
            dismissPopupWindow()
            loadUrl("javascript:highlightSelection()")
        }
        binding.meaningSelection.setOnClickListener {
            dismissPopupWindow()
            loadUrl("javascript:requestMeaning()")
        }
    }


    /**
     * The system's own floating text-selection toolbar (copy, share, select all, ...), with the
     * reader's actions added to it: highlight, add to favorites, meaning. Looks and behaves like
     * text selection everywhere else on the phone, instead of a custom popup.
     */
    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? =
        super.startActionMode(ReaderActionModeCallback(callback), ActionMode.TYPE_FLOATING)

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? =
        super.startActionMode(ReaderActionModeCallback(callback), ActionMode.TYPE_FLOATING)

    private inner class ReaderActionModeCallback(
        private val system: ActionMode.Callback?,
    ) : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val created = system?.onCreateActionMode(mode, menu) ?: true
            // Before the system items, so the reader's actions are visible without "more".
            // On the toolbar: highlight and meaning ("always"), then the system's copy / share;
            // add-to-favorites goes under "more", after the system items.
            menu.add(Menu.NONE, R.id.highlightSelection, 0, R.string.highlight)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(Menu.NONE, R.id.meaningSelection, 1, R.string.meaning)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(Menu.NONE, R.id.addSelectionToFavirite, 20, R.string.addToFavorite)
            return created
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
            system?.onPrepareActionMode(mode, menu) ?: false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val script = when (item.itemId) {
                R.id.highlightSelection -> "highlightSelection()"
                R.id.meaningSelection -> "requestMeaning()"
                R.id.addSelectionToFavirite -> "onTextSelectionItemClicked(${item.itemId})"
                else -> return system?.onActionItemClicked(mode, item) ?: false
            }
            // Finishing the action mode clears the WebView's selection, so it must wait until
            // the script has read it — with loadUrl() + an immediate finish() the two raced and
            // the action often saw an empty selection (nothing happened until a second try).
            evaluateJavascript(script) { mode.finish() }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            system?.onDestroyActionMode(mode)
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            (system as? ActionMode.Callback2)?.onGetContentRect(mode, view, outRect)
                ?: super.onGetContentRect(mode, view, outRect)
        }
    }

    @JavascriptInterface
    fun setSelectionRect(left: Int, top: Int, right: Int, bottom: Int) {

        val currentSelectionRect = Rect()
        currentSelectionRect.left = (left * density).toInt()
        currentSelectionRect.top = (top * density).toInt()
        currentSelectionRect.right = (right * density).toInt()
        currentSelectionRect.bottom = (bottom * density).toInt()
        Log.d(LOG_TAG, "-> setSelectionRect -> $currentSelectionRect")

        computeTextSelectionRect(currentSelectionRect)
        uiHandler.post { showTextSelectionPopup() }
    }

    /** "المعنى" on a selection (see requestMeaning() in Bridge.js): the word and its surrounding text. */
    @JavascriptInterface
    fun onMeaningRequested(selectedText: String?, context: String?) {
        val word = selectedText?.trim().orEmpty()
        if (word.isEmpty()) return
        uiHandler.post { onRequestMeaning?.invoke(word, context.orEmpty()) }
    }

    @JavascriptInterface
    fun onTextSelectionItemClicked(id: Int, selectedText: String?) {

        uiHandler.post { loadUrl("javascript:clearSelection()") }

        when (id) {
            R.id.copySelection -> {
                Log.v(LOG_TAG, "-> onTextSelectionItemClicked -> copySelection -> $selectedText")
                UiUtil.copyToClipboard(context, selectedText)
            }

            R.id.shareSelection -> {
                Log.v(LOG_TAG, "-> onTextSelectionItemClicked -> shareSelection -> $selectedText")
                UiUtil.share(context, selectedText)
            }

            R.id.addSelectionToFavirite -> {
                Log.v(
                    LOG_TAG,
                    "-> onTextSelectionItemClicked -> addSelectionToFavorite -> $selectedText, currentPageIndex= $currentPageIndex",
                )
                selectedText?.let {

                    FolioReader.get().onAddQuoteToFavorite(
                        quote = it,
                        pageIndex = currentPageIndex,
                        pageHref = currentPageHref?:""
                    )
                }
            }

            else -> {
                Log.w(LOG_TAG, "-> onTextSelectionItemClicked -> unknown id = $id")
            }
        }
    }

    @JavascriptInterface
    fun onHighlightSelection(text: String?, before: String?) {
        if (text.isNullOrBlank()) return
        uiHandler.post { onCreateHighlight?.invoke(text, before.orEmpty()) }
    }

    @JavascriptInterface
    fun onHighlightClicked(id: String?) {
        id ?: return
        uiHandler.post { onHighlightClicked?.invoke(id) }
    }

    @JavascriptInterface
    fun isPopupShowing(): Boolean {
        return popupWindow.isShowing
    }

    @JavascriptInterface
    fun dismissPopupWindow(): Boolean {
//        Log.d(LOG_TAG, "-> dismissPopupWindow -> " + parentFragment.spineItem.href)
        val wasShowing = popupWindow.isShowing
        if (Looper.getMainLooper().thread == Thread.currentThread()) {
            popupWindow.dismiss()
        } else {
            uiHandler.post { popupWindow.dismiss() }
        }
        selectionRect = Rect()
        return wasShowing
    }

    private fun computeViewportRect(): Rect {
        Log.v(LOG_TAG, "-> computeViewportRect");
        val displayMetrics = resources.displayMetrics
        val viewportRect = Rect()

        viewportRect.left = 0
        viewportRect.top = 0
//        if (distractionFreeMode) {
        viewportRect.right = displayMetrics!!.widthPixels
//        } else {
//            viewportRect.right = displayMetrics!!.widthPixels - viewportRect.right
//        }
        viewportRect.bottom = displayMetrics.heightPixels

        return viewportRect
    }


    private fun getViewportRect(unit: DisplayUnit): Rect {

        val viewportRect = computeViewportRect()
        when (unit) {
            DisplayUnit.PX -> return viewportRect

            DisplayUnit.DP -> {
                viewportRect.left /= density.toInt()
                viewportRect.top /= density.toInt()
                viewportRect.right /= density.toInt()
                viewportRect.bottom /= density.toInt()
                return viewportRect
            }

            DisplayUnit.CSS_PX -> {
                viewportRect.left = ceil((viewportRect.left / density).toDouble()).toInt()
                viewportRect.top = ceil((viewportRect.top / density).toDouble()).toInt()
                viewportRect.right = ceil((viewportRect.right / density).toDouble()).toInt()
                viewportRect.bottom = ceil((viewportRect.bottom / density).toDouble()).toInt()
                return viewportRect
            }

            else -> throw IllegalArgumentException("-> Illegal argument -> unit = $unit")
        }
    }

    private fun computeTextSelectionRect(currentSelectionRect: Rect) {
        Log.v(LOG_TAG, "-> computeTextSelectionRect")

        val viewportRect = getViewportRect(DisplayUnit.PX)
        Log.d(LOG_TAG, "-> viewportRect -> $viewportRect")

        if (!Rect.intersects(viewportRect, currentSelectionRect)) {
            Log.i(LOG_TAG, "-> currentSelectionRect doesn't intersects viewportRect")
            uiHandler.post {
                popupWindow.dismiss()
            }
            return
        }
        Log.i(LOG_TAG, "-> currentSelectionRect intersects viewportRect")

        if (selectionRect == currentSelectionRect) {
            Log.i(
                LOG_TAG, "-> setSelectionRect -> currentSelectionRect is equal to previous " +
                        "selectionRect so no need to computeTextSelectionRect and show popupWindow again"
            )
            return
        }

        Log.i(
            LOG_TAG, "-> setSelectionRect -> currentSelectionRect is not equal to previous " +
                    "selectionRect so computeTextSelectionRect and show popupWindow"
        )
        selectionRect = currentSelectionRect

        val aboveSelectionRect = Rect(viewportRect)
        aboveSelectionRect.bottom = selectionRect.top - (8 * density).toInt()
        val belowSelectionRect = Rect(viewportRect)
        belowSelectionRect.top = selectionRect.bottom + handleHeight


        // Priority to show popupWindow will be as following -
        // 1. Show popupWindow below selectionRect, if space available
        // 2. Show popupWindow above selectionRect, if space available
        // 3. Show popupWindow in the middle of selectionRect

        //popupRect initialisation for belowSelectionRect
        popupRect.left = viewportRect.left
        popupRect.top = belowSelectionRect.top
        popupRect.right = popupRect.left + viewTextSelectionNew.measuredWidth
        popupRect.bottom = popupRect.top + viewTextSelectionNew.measuredHeight
        //Log.d(LOG_TAG, "-> Pre decision popupRect -> " + popupRect);

        val popupY: Int
        if (belowSelectionRect.contains(popupRect)) {
            Log.i(LOG_TAG, "-> show below")
            popupY = belowSelectionRect.top

        } else {

            // popupRect initialisation for aboveSelectionRect
            popupRect.top = aboveSelectionRect.top
            popupRect.bottom = popupRect.top + viewTextSelectionNew.measuredHeight

            popupY = if (aboveSelectionRect.contains(popupRect)) {
                Log.i(LOG_TAG, "-> show above")
                aboveSelectionRect.bottom - popupRect.height()

            } else {

                Log.i(LOG_TAG, "-> show in middle")
                val popupYDiff = (viewTextSelectionNew.measuredHeight - selectionRect.height()) / 2
                selectionRect.top - popupYDiff
            }
        }

        val popupXDiff = (viewTextSelectionNew.measuredWidth - selectionRect.width()) / 2
        val popupX = selectionRect.left - popupXDiff

        popupRect.offsetTo(popupX, popupY)
        //Log.d(LOG_TAG, "-> Post decision popupRect -> " + popupRect);

        // Check if popupRect left side is going outside of the viewportRect
        if (popupRect.left < viewportRect.left) {
            popupRect.right += 0 - popupRect.left
            popupRect.left = 0
        }

        // Check if popupRect right side is going outside of the viewportRect
        if (popupRect.right > viewportRect.right) {
            val dx = popupRect.right - viewportRect.right
            popupRect.left -= dx
            popupRect.right -= dx
        }
    }

    private fun showTextSelectionPopup() {
        Log.v(LOG_TAG, "-> showTextSelectionPopup")
        Log.d(LOG_TAG, "-> showTextSelectionPopup -> To be laid out popupRect -> $popupRect")
        popupWindow.dismiss()
        val isScrollingRunnable = Runnable {
            Log.i(
                LOG_TAG,
                "-> Stopped scrolling, show Popup, fullScreenMode= ${fullScreenMode.value}"
            )
            val selectedFontSize = AppFonts.selectedFontSizeCssClass()
            val currentFontSize = when (selectedFontSize) {
                "textSizeOne" -> 13
                "textSizeTwo" -> 15
                "textSizeThree" -> 17
                "textSizeFour" -> 19
                "textSizeFive" -> 21
                else -> 0
            }
            val lineOffset = (currentFontSize * density).toInt() + currentFontSize
            Log.e(LOG_TAG, "selectedFontSize: $selectedFontSize ")
            Log.e(LOG_TAG, "currentFontSize: $currentFontSize ")
            val verticalOffset = 16 + if (fullScreenMode.value) lineOffset else lineOffset * 3
            popupWindow.dismiss()
            popupWindow = PopupWindow(
                viewTextSelectionNew,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            popupWindow.isClippingEnabled = false
            popupWindow.showAtLocation(
                this@CustomWebView,
                Gravity.NO_GRAVITY,
                popupRect.left,
                popupRect.top + verticalOffset
            )

        }

        uiHandler.postDelayed(isScrollingRunnable, IS_SCROLLING_CHECK_TIMER.toLong())
    }

    // No horizontal scroll range at all: while a chapter loads, the (right-to-left) page can
    // measure a few pixels wider than the screen, and the WebView then scrolled it sideways for a
    // frame, leaving empty strips at the edges.
    override fun computeHorizontalScrollRange(): Int = width

    override fun computeHorizontalScrollExtent(): Int = width

    override fun overScrollBy(
        deltaX: Int, deltaY: Int, scrollX: Int, scrollY: Int, scrollRangeX: Int, scrollRangeY: Int,
        maxOverScrollX: Int, maxOverScrollY: Int, isTouchEvent: Boolean,
    ): Boolean = super.overScrollBy(0, deltaY, 0, scrollY, 0, scrollRangeY, 0, maxOverScrollY, isTouchEvent)

    companion object {
        private const val IS_SCROLLING_CHECK_TIMER = 100
    }
}
