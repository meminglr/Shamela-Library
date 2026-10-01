package com.shamela.apptheme.presentation.util.notifications

import android.app.NotificationManager
import androidx.annotation.StringRes
import com.shamela.apptheme.R

/**
 * [id] is the persistent channel id and must never change or depend on the UI language (it
 * equals the Arabic channel name that earlier versions used as id, so existing channels are kept).
 */
enum class ChannelType(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val importance: Int,
) {
    BookPreparation(
        id = "تحضير الكتب",
        title = R.string.books_preperation_notificatinos,
        description = R.string.books_preperation_notification_description,
        importance = NotificationManager.IMPORTANCE_HIGH,
    ),
    DatabaseMigration(
        id = "تحديث قاعدة البيانات",
        title = R.string.database_update_notifications,
        description = R.string.database_update_notifications_description,
        importance = NotificationManager.IMPORTANCE_HIGH,
    ),
}