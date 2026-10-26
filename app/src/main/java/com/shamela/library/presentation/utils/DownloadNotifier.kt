package com.shamela.library.presentation.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shamela.library.R
import com.shamela.library.domain.model.DownloadStatus

/**
 * One notification for all book downloads: "Downloading 3 / 10 books · 45%" with a progress bar
 * while anything is active, then a summary ("10 books downloaded, 1 failed") when the queue drains.
 */
object DownloadNotifier {
    private const val CHANNEL_ID = "book-downloads"
    private const val PROGRESS_ID = 4101
    private const val SUMMARY_ID = 4102

    private var lastUpdate = 0L
    private var wasActive = false
    private var completedInSession = 0
    private var failedAtStart = 0

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.dl_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.dl_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @Synchronized
    fun onBookCompleted() {
        completedInSession++
    }

    @Synchronized
    fun update(context: Context, items: List<DownloadItem>) {
        val active = items.filter { it.status.isActive }
        val failed = items.count { it.status is DownloadStatus.Failed }
        val manager = NotificationManagerCompat.from(context)
        if (!canNotify(context)) return

        if (active.isEmpty()) {
            manager.cancel(PROGRESS_ID)
            if (wasActive) {
                wasActive = false
                val newFailures = (failed - failedAtStart).coerceAtLeast(0)
                if (completedInSession > 0 || newFailures > 0) {
                    val text = if (newFailures > 0)
                        context.getString(R.string.dl_notif_done_with_failures, completedInSession, newFailures)
                    else context.getString(R.string.dl_notif_done_text, completedInSession)
                    manager.notify(
                        SUMMARY_ID,
                        builder(context)
                            .setContentTitle(context.getString(R.string.dl_notif_done_title))
                            .setContentText(text)
                            .setAutoCancel(true)
                            .build()
                    )
                }
                completedInSession = 0
            }
            return
        }

        if (!wasActive) {
            wasActive = true
            failedAtStart = failed
            manager.cancel(SUMMARY_ID)
        }
        // Progress arrives several times a second; the notification needs far less.
        val now = SystemClock.elapsedRealtime()
        if (now - lastUpdate < 1000) return
        lastUpdate = now

        val running = active.mapNotNull { it.status as? DownloadStatus.Downloading }
        val waitingForNetwork = active.isNotEmpty() && active.all {
            (it.status as? DownloadStatus.Waiting)?.noNetwork == true || it.status == DownloadStatus.Queued
        } && active.any { it.status is DownloadStatus.Waiting }
        val done = completedInSession
        val total = done + active.size
        val text = when {
            waitingForNetwork -> context.getString(R.string.dl_notif_waiting)
            running.size == 1 -> {
                val s = running.first()
                if (s.total > 0 && s.unitIsPages) "${active.first { it.status === s }.title} · " +
                    context.getString(R.string.dl_progress_pages, s.progress, s.bytes, s.total)
                else if (s.total > 0) "${active.first { it.status === s }.title} · ${s.progress}% · " +
                    "${Formatter.formatShortFileSize(context, s.bytes)} / ${Formatter.formatShortFileSize(context, s.total)}"
                else active.first { it.status === s }.title
            }
            else -> context.getString(R.string.dl_notif_progress_text, done, total)
        }
        val withTotals = running.filter { it.total > 0 }
        // Overall progress: finished books count as 100%, the rest by their bytes.
        val percent = if (total > 0) {
            val activeProgress = active.sumOf { item ->
                when (val s = item.status) {
                    is DownloadStatus.Downloading -> if (s.total > 0) s.progress else 0
                    else -> 0
                }
            }
            ((done * 100 + activeProgress) / total).coerceIn(0, 100)
        } else 0

        manager.notify(
            PROGRESS_ID,
            builder(context)
                .setContentTitle(context.getString(R.string.dl_notif_progress_title, total))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, percent, withTotals.isEmpty() && done == 0)
                .build()
        )
    }

    private fun builder(context: Context) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(com.shamela.apptheme.R.drawable.ic_notification)
        .setSilent(true)
        .setContentIntent(openAppIntent(context))

    private fun openAppIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
