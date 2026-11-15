package ru.petproject.giribro.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.petproject.giribro.MainActivity
import ru.petproject.giribro.R

object BackupNotifications {

    private const val CHANNEL_ID = "backup_reminder"
    private const val NOTIFICATION_ID = 1002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Резервные копии",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Напоминания о сохранении дневника и ошибки автокопий"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyReminder(context: Context) {
        notify(
            context = context,
            title = "Пора сохранить резервную копию",
            text = "Последняя копия старше 30 дней. Откройте настройки и сохраните дневник."
        )
    }

    fun notifyFailure(context: Context, message: String) {
        notify(
            context = context,
            title = "Автокопия не создалась",
            text = message
        )
    }

    private fun notify(context: Context, title: String, text: String) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SHORTCUT, "settings")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kettlebell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
