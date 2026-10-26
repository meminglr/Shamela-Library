package com.folioreader.reader

import android.content.Context
import com.folioreader.model.locators.SearchLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Highlight colors (light enough to keep black text readable on every page color). */
enum class HighlightColor(val css: String, val argb: Long) {
    Yellow("#FFE082", 0xFFFFE082),
    Green("#C5E1A5", 0xFFC5E1A5),
    Blue("#90CAF9", 0xFF90CAF9),
    Pink("#F8BBD0", 0xFFF8BBD0),
}

/**
 * A colored mark on a passage, with an optional note. [before] is the text just before the
 * passage in its chapter, so the same words elsewhere in the chapter aren't marked by mistake.
 */
data class Highlight(
    val id: String = UUID.randomUUID().toString(),
    val href: String,
    val chapterIndex: Int,
    val text: String,
    val before: String,
    val color: HighlightColor = HighlightColor.Yellow,
    val note: String = "",
    val created: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("href", href).put("chapter", chapterIndex).put("text", text)
        .put("before", before).put("color", color.name).put("note", note).put("created", created)

    /** What the page script needs to draw it. */
    fun toScriptJson(): JSONObject = JSONObject()
        .put("id", id).put("text", text).put("before", before).put("color", color.css).put("note", note)

    companion object {
        fun fromJson(o: JSONObject) = Highlight(
            id = o.getString("id"),
            href = o.getString("href"),
            chapterIndex = o.optInt("chapter", 0),
            text = o.getString("text"),
            before = o.optString("before"),
            color = runCatching { HighlightColor.valueOf(o.getString("color")) }.getOrDefault(HighlightColor.Yellow),
            note = o.optString("note"),
            created = o.optLong("created"),
        )
    }
}

/** Highlights of the open book, saved as one small JSON file per book. */
object HighlightStore {
    private val _highlights = MutableStateFlow<List<Highlight>>(emptyList())
    val highlights: StateFlow<List<Highlight>> = _highlights.asStateFlow()
    private var file: File? = null

    fun open(context: Context, bookKey: String) {
        val f = File(context.filesDir, "highlights/$bookKey.json")
        file = f
        _highlights.value = runCatching {
            val array = JSONArray(f.readText())
            List(array.length()) { Highlight.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun add(highlight: Highlight) = save(_highlights.value + highlight)

    fun update(id: String, transform: (Highlight) -> Highlight) =
        save(_highlights.value.map { if (it.id == id) transform(it) else it })

    fun remove(id: String) = save(_highlights.value.filterNot { it.id == id })

    fun get(id: String): Highlight? = _highlights.value.firstOrNull { it.id == id }

    /** JavaScript that draws this chapter's highlights. */
    fun applyJs(href: String?): String {
        val array = JSONArray()
        _highlights.value.filter { it.href == href }.forEach { array.put(it.toScriptJson()) }
        return "applyUserHighlights($array)"
    }

    private fun save(list: List<Highlight>) {
        _highlights.value = list
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            f.writeText(array.toString())
        }
    }
}

/**
 * The in-book search the reader came from: its results, so the reader can step to the previous
 * or next match without going back to the results list.
 */
object SearchSession {
    data class State(
        val bookId: String = "",
        val query: String = "",
        val results: List<SearchLocator> = emptyList(),
        val index: Int = -1,
    ) {
        val isActive: Boolean get() = index in results.indices
        val current: SearchLocator? get() = results.getOrNull(index)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(bookId: String, query: String, results: List<SearchLocator>, index: Int) {
        _state.value = State(bookId, query, results, index)
    }

    /** Moves by [delta] results; returns the new current result, or null at either end. */
    fun step(delta: Int): SearchLocator? {
        val s = _state.value
        val next = s.index + delta
        if (next !in s.results.indices) return null
        _state.value = s.copy(index = next)
        return s.results[next]
    }

    /** Hides the result navigator but keeps the results for "back to results". */
    fun close() {
        _state.value = _state.value.copy(index = -1)
    }

    fun resultsFor(bookId: String): State? = _state.value.takeIf { it.bookId == bookId && it.results.isNotEmpty() }
}

/** Where reading stopped in each book: the chapter and how far down it (0..1). */
object ReadingPositionStore {
    private const val PREFS = "reading_position"

    fun save(context: Context, bookKey: String, href: String, fraction: Float) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("$bookKey.href", href)
            .putFloat("$bookKey.fraction", fraction.coerceIn(0f, 1f))
            .apply()
    }

    fun load(context: Context, bookKey: String): Pair<String, Float>? {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val href = p.getString("$bookKey.href", null) ?: return null
        return href to p.getFloat("$bookKey.fraction", 0f)
    }
}
