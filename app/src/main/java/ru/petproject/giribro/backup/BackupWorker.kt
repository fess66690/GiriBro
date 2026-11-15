package ru.petproject.giribro.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.petproject.giribro.GiriBroApp

/**
 * Раз в сутки проверяет, не пора ли сделать автокопию и не пора ли напомнить
 * о ней. WorkManager сам разбудит приложение, даже если оно давно закрыто.
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? GiriBroApp ?: return Result.success()
        BackupNotifications.ensureChannel(app)

        when (val outcome = app.backupService.autoBackup(force = false)) {
            is BackupOutcome.Failed -> {
                BackupNotifications.notifyFailure(app, outcome.message)
                return Result.success()
            }
            else -> Unit
        }

        if (app.backupService.shouldRemind()) {
            BackupNotifications.notifyReminder(app)
        }
        return Result.success()
    }
}
