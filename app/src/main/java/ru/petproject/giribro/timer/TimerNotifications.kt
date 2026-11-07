package ru.petproject.giribro.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object TimerNotifications {

    const val CHANNEL_ID = "interval_timer"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Интервальный таймер",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Работа, отдых и раунды во время тренировки"
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }
}
