package com.shamela.library.domain.model

sealed class DownloadStatus {
    object NotDownloaded : DownloadStatus()

    /** Waiting for a free download slot (at most a few books download at once). */
    object Queued : DownloadStatus()

    /**
     * Transferring. [total] is -1 until the server reports the size; [progress] is 0..100.
     * [bytesPerSecond] is the recent transfer speed, 0 when unknown. With [unitIsPages] (a
     * shamela.ws book being built on the device) [bytes] and [total] count pages instead of bytes.
     */
    data class Downloading(
        val progress: Int,
        val bytes: Long = 0L,
        val total: Long = -1L,
        val bytesPerSecond: Long = 0L,
        val unitIsPages: Boolean = false,
    ) : DownloadStatus()

    /** Interrupted (no connection or a server hiccup); resumes from where it stopped by itself. */
    data class Waiting(val bytes: Long = 0L, val total: Long = -1L, val noNetwork: Boolean = true) : DownloadStatus()

    /** Gave up (e.g. the book isn't on the server, or the disk is full); the user can retry. */
    data class Failed(val reason: Int) : DownloadStatus()

    object Downloaded : DownloadStatus()

    /** True while the download is in the queue, running, or waiting to resume. */
    val isActive: Boolean get() = this is Queued || this is Downloading || this is Waiting
}
