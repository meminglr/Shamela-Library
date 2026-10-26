package com.shamela.library.presentation.utils

import com.shamela.library.domain.model.Book
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Deletes books in the background, app-wide, like [BooksDownloadManager] downloads them: the screen
 * that asked can be left, a floating bar shows the progress on every screen and a sheet the details.
 * Requests made while a deletion runs are queued behind it.
 */
object BookDeletionManager {
    data class State(
        val total: Int = 0,
        val done: Int = 0,
        val deleted: Int = 0,
        val current: String = "",
        val running: Boolean = false,
        /** The last run ended (all done, or stopped); the bar shows the result briefly. */
        val finished: Boolean = false,
        val stopped: Boolean = false,
        /** Titles deleted in this run, newest first. */
        val recent: List<String> = emptyList(),
    ) {
        val visible: Boolean get() = running || finished
    }

    private class Batch(
        val books: List<Book>,
        val deleteBook: suspend (Book) -> Boolean,
        val onFinished: (suspend (completed: Boolean) -> Unit)?,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ArrayDeque<Batch>()
    private var job: Job? = null
    private var hideJob: Job? = null

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _deletedIds = MutableSharedFlow<String>(extraBufferCapacity = 1024)
    /** Each book id as soon as it's deleted, so open screens can drop it from their lists. */
    val deletedIds: SharedFlow<String> = _deletedIds.asSharedFlow()

    /**
     * Queues [books] for deletion with [deleteBook] (the use case that removes the file, the search
     * index and the database row). [onFinished] runs after this batch, with whether it completed.
     */
    @Synchronized
    fun delete(
        books: List<Book>,
        deleteBook: suspend (Book) -> Boolean,
        onFinished: (suspend (completed: Boolean) -> Unit)? = null,
    ) {
        if (books.isEmpty()) {
            scope.launch { onFinished?.invoke(true) }
            return
        }
        pending.addLast(Batch(books, deleteBook, onFinished))
        hideJob?.cancel()
        _state.update {
            if (it.running) it.copy(total = it.total + books.size)
            else State(total = books.size, running = true, current = books.first().title)
        }
        if (job?.isActive != true) job = scope.launch { run() }
    }

    /** Stops after the current book; queued books are kept. */
    @Synchronized
    fun stop() {
        job?.cancel()
    }

    /** Hides the finished bar (also happens by itself after a few seconds). */
    fun dismiss() {
        if (!_state.value.running) _state.value = State()
    }

    private suspend fun run() {
        var completed = true
        try {
            while (true) {
                val batch = synchronized(this) { pending.removeFirstOrNull() } ?: break
                var batchCompleted = false
                try {
                    for (book in batch.books) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        _state.update { it.copy(current = book.title) }
                        // Stop takes effect between books: the one in progress is finished (and counted).
                        val ok = withContext(NonCancellable) {
                            runCatching { batch.deleteBook(book) }.getOrDefault(false)
                        }
                        if (ok) _deletedIds.tryEmit(book.id)
                        _state.update {
                            it.copy(
                                done = it.done + 1,
                                deleted = it.deleted + if (ok) 1 else 0,
                                recent = if (ok) (listOf(book.title) + it.recent).take(200) else it.recent,
                            )
                        }
                    }
                    batchCompleted = true
                } finally {
                    withContext(NonCancellable) { batch.onFinished?.invoke(batchCompleted) }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            completed = false
            // Stopped: the rest of the queue is dropped too, but each batch still hears about it.
            val dropped = synchronized(this) { pending.toList().also { pending.clear() } }
            withContext(NonCancellable) { dropped.forEach { it.onFinished?.invoke(false) } }
        } finally {
            _state.update { it.copy(running = false, finished = true, stopped = !completed, current = "") }
            hideJob = scope.launch {
                delay(4000)
                if (!_state.value.running) _state.value = State()
            }
        }
    }
}
