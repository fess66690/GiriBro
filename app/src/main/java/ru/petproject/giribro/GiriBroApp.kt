package ru.petproject.giribro

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.petproject.giribro.backup.BackupNotifications
import ru.petproject.giribro.backup.BackupScheduler
import ru.petproject.giribro.backup.BackupService
import ru.petproject.giribro.data.AppDatabase
import ru.petproject.giribro.data.BackupRepository
import ru.petproject.giribro.data.BodyMeasurementRepository
import ru.petproject.giribro.data.DemoData
import ru.petproject.giribro.data.SettingsRepository
import ru.petproject.giribro.data.WorkoutDraftStore
import ru.petproject.giribro.data.WorkoutRepository
import ru.petproject.giribro.logging.AppLog
import ru.petproject.giribro.timer.TimerNotifications

class GiriBroApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val workouts: WorkoutRepository by lazy { WorkoutRepository(database.workoutDao()) }
    val bodyMeasurements: BodyMeasurementRepository by lazy {
        BodyMeasurementRepository(database.bodyMeasurementDao())
    }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val backups: BackupRepository by lazy { BackupRepository(this) }
    val drafts: WorkoutDraftStore by lazy { WorkoutDraftStore(this) }
    val backupService: BackupService by lazy {
        BackupService(this, workouts, bodyMeasurements, settings, backups)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        runCatching { TimerNotifications.ensureChannel(this) }
            .onFailure { AppLog.e(TAG, "Не удалось создать канал уведомлений", it) }
        runCatching { BackupNotifications.ensureChannel(this) }
            .onFailure { AppLog.e(TAG, "Не удалось создать канал резервных копий", it) }
        runCatching { BackupScheduler.schedule(this) }
            .onFailure { AppLog.e(TAG, "Не удалось запланировать автокопию", it) }
        // Демо-данные нужны только для разработки и скриншотов.
        // В release пользователь начинает с пустого дневника.
        if (BuildConfig.DEBUG) {
            seedDemoIfNeeded()
        }
        appScope.launch {
            runCatching { backupService.autoBackup(force = false) }
                .onFailure { AppLog.e(TAG, "Не удалось проверить автокопию", it) }
        }
    }

    private fun seedDemoIfNeeded() {
        val alreadySeeded = runCatching { settings.demoSeeded }.getOrDefault(true)
        if (alreadySeeded) return

        runCatching { settings.demoSeeded = true }
            .onFailure {
                AppLog.e(TAG, "Не удалось сохранить флаг демо-данных", it)
                return
            }

        appScope.launch {
            DemoData.workouts().forEach { workout ->
                runCatching { workouts.save(workout) }
                    .onFailure { AppLog.e(TAG, "Не удалось сохранить демо-тренировку", it) }
            }
            DemoData.measurements().forEach { measurement ->
                runCatching { bodyMeasurements.save(measurement) }
                    .onFailure { AppLog.e(TAG, "Не удалось сохранить демо-замер", it) }
            }
        }
    }

    private companion object {
        const val TAG = "GiriBro"
    }
}
