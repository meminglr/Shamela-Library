package com.folioreader.ui.activity.folioActivity.book


data class BookState(
    val isLoading: Boolean = true,
    val mimeType: String = "text/html",
    val pagesMap: Map<Int, Pair<String, String>> = emptyMap(), //Map of pageIndex->(pageUrl to pageHtmlContent)
    val isAppBarsVisible: Boolean = true,
    val isMenuVisible: Boolean = false,
    val currentPageText: String = "0",
    // Word-meaning (AI) sheet state: the selection and the conversation about it as shown
    // (the AI's answers and the reader's follow-up questions, without the opening prompt).
    val showMeaningSheet: Boolean = false,
    val meaningQuery: String = "",
    val meaningLoading: Boolean = false,
    val meaningMessages: List<ChatMessage> = emptyList(),
    val meaningError: MeaningError? = null,
)