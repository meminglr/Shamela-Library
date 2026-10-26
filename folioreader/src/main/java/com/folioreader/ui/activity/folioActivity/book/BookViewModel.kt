package com.folioreader.ui.activity.folioActivity.book


import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folioreader.ui.activity.folioActivity.FolioActivity
import com.folioreader.ui.base.HtmlUtil
import com.folioreader.util.AppUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.Publication
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class BookViewModel : ViewModel() {
    private val _state = MutableStateFlow(BookState())
    val state = _state.asStateFlow()
    private val cachedPages = mutableMapOf<Int, Pair<String, String>>()
//    val cachedWebViews = mutableStateMapOf<Int, WebView>()


    fun onEvent(event: BookEvent) {
        when (event) {
            is BookEvent.OnChangeSelectedPage -> {
                clearOldWebViews(event.pageIndex)
                onEvent(BookEvent.OnCurrentPageTextChanged(event.pageIndex.toString()))
                fetchPage(event)
            }

            is BookEvent.OnCurrentPageTextChanged -> _state.update {
                it.copy(currentPageText = event.newPage)
            }

            BookEvent.ToggleAppBarsVisibility -> _state.update {
                it.copy(isAppBarsVisible = !it.isAppBarsVisible)
            }

            BookEvent.ToggleMenuVisibility -> _state.update {
                it.copy(isMenuVisible = !it.isMenuVisible)
            }

            BookEvent.DismissMenu -> _state.update {
                it.copy(isMenuVisible = false)
            }

            BookEvent.ClearCachedPages -> {
                cachedPages.clear()
//                cachedWebViews.clear()
                _state.update { it.copy(pagesMap = emptyMap()) }
            }

            is BookEvent.ShowMeaningSheet -> {
                val selection = event.selectedText.trim()
                // Answer in the app's language: Turkish UI → Turkish, otherwise Arabic.
                val turkish = event.context.resources.configuration.locales[0].language == "tr"
                conversation = listOf(ChatMessage(fromUser = true, MeaningApi.firstPrompt(selection, event.passage, turkish)))
                _state.update { it.copy(showMeaningSheet = true, meaningQuery = selection, meaningMessages = emptyList()) }
                requestReply()
            }

            is BookEvent.AskMeaningQuestion -> {
                val question = event.question.trim()
                if (question.isEmpty() || _state.value.meaningLoading) return
                val message = ChatMessage(fromUser = true, question)
                conversation = conversation + message
                _state.update { it.copy(meaningMessages = it.meaningMessages + message) }
                requestReply()
            }

            // The conversation still ends with the unanswered message; just send it again.
            BookEvent.RetryMeaning -> requestReply()

            BookEvent.DismissMeaningSheet -> {
                meaningJob?.cancel()
                _state.update { it.copy(showMeaningSheet = false) }
            }
        }
    }

    private var meaningJob: Job? = null

    /** Everything sent to the AI so far, starting with the opening prompt. */
    private var conversation: List<ChatMessage> = emptyList()

    private fun requestReply() {
        val messages = conversation
        _state.update { it.copy(meaningLoading = true, meaningError = null) }
        meaningJob?.cancel()
        meaningJob = viewModelScope.launch {
            try {
                val answer = ChatMessage(fromUser = false, MeaningApi.reply(messages))
                conversation = messages + answer
                _state.update { it.copy(meaningLoading = false, meaningMessages = it.meaningMessages + answer) }
            } catch (e: MeaningApi.Failure) {
                _state.update { it.copy(meaningLoading = false, meaningError = e.error) }
            }
        }
    }

    private fun clearOldWebViews(currentIndex: Int) {
        val maxCacheDistance = 5
//        cachedWebViews.keys.toList().forEach { key ->
//            if (abs(key - currentIndex) > maxCacheDistance) cachedWebViews.remove(key)
//        }
    }

    private fun fetchPage(event: BookEvent.OnChangeSelectedPage) {
        viewModelScope.launch {
            getBookPages(
                context = event.context,
                pageIndex = event.pageIndex,
                totalPages = event.publication.readingOrder.lastIndex,
                fontFamily = event.fontFamilyCssClass,
                publication = event.publication,
                isNightMode = event.isNightMode,
                fontSize = event.fontSizeCssClass,
                streamUrl = event.streamUrl
            ).collect { pageMap ->
                _state.update {
                    it.copy(
                        pagesMap = it.pagesMap + pageMap,
                        isLoading = false
                    )
                }
            }
        }
    }


    private fun getBookPages(
        context: Context,
        pageIndex: Int,
        totalPages: Int,
        fontFamily: String,
        publication: Publication,
        isNightMode: Boolean,
        fontSize: String,
        streamUrl: String
    ) = flow {
        // Emit the requested page first so the visible page never waits behind prefetch requests.
        val currentCached = cachedPages[pageIndex]
        if (currentCached != null) {
            emit(mapOf(pageIndex to currentCached))
        } else {
            fetchSinglePage(pageIndex, publication, streamUrl, context, fontFamily, isNightMode, fontSize)?.let { data ->
                cachedPages[pageIndex] = data
                emit(mapOf(pageIndex to data))
            }
        }

        // Prefetch surrounding window sorted by proximity — N±1 first, so the
        // pages the user is most likely to swipe to get data as early as possible.
        val range = (maxOf(0, pageIndex - 5))..(minOf(pageIndex + 5, totalPages))
        val sortedPages = range.sortedBy { abs(it - pageIndex) }.filter { it != pageIndex }
        for (page in sortedPages) {
            val cachedPage = cachedPages[page]
            if (cachedPage != null) {
                emit(mapOf(page to cachedPage))
                continue
            }
            fetchSinglePage(page, publication, streamUrl, context, fontFamily, isNightMode, fontSize)?.let { data ->
                cachedPages[page] = data
                emit(mapOf(page to data))
            }
        }
    }

    private suspend fun fetchSinglePage(
        page: Int,
        publication: Publication,
        streamUrl: String,
        context: Context,
        fontFamily: String,
        isNightMode: Boolean,
        fontSize: String,
    ): Pair<String, String>? {
        val href = publication.readingOrder[page].href?.removePrefix("/") ?: return null
        val pageUrl = "$streamUrl$href"
        val html = getHtmlData(pageUrl)
        if (html.isEmpty()) return null
        val styledHtml = HtmlUtil.getHtmlContent(context, html, fontFamily, isNightMode, fontSize)
        return Pair(pageUrl, styledHtml)
    }

    private suspend fun getHtmlData(urlString: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            BufferedReader(InputStreamReader(connection.inputStream, AppUtil.charsetNameForURLConnection(connection))).use { reader ->
                buildString {
                    reader.lineSequence().forEach { appendLine(it) }
                }
            }
        }.onFailure {
            Log.e(FolioActivity.LOG_TAG, "HtmlTask failed", it)
        }.getOrDefault("")
    }
}