package ru.petproject.giribro

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.petproject.giribro.data.AppDatabase
import ru.petproject.giribro.data.BodyMeasurementRepository
import ru.petproject.giribro.data.DemoData
import ru.petproject.giribro.data.SettingsRepository
import ru.petproject.giribro.data.WorkoutRepository
import ru.petproject.giribro.timer.TimerNotifications

class GiriBroApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val workouts: WorkoutRepository by lazy { WorkoutRepository(database.workoutDao()) }
    val bodyMeasurements: BodyMeasurementRepository by lazy {
        BodyMeasurementRepository(database.bodyMeasurementDao())
    }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        runCatching { TimerNotifications.ensureChannel(this) }
            .onFailure { Log.e(TAG, "Не удалось создать канал уведомлений", it) }
        // Демо-данные нужны только для разработки и скриншотов.
        // В release пользователь начинает с пустого дневника.
        if (BuildConfig.DEBUG) {
            seedDemoIfNeeded()
        }
    }

    private fun seedDemoIfNeeded() {
        val alreadySeeded = runCatching { settings.demoSeeded }.getOrDefault(true)
        if (alreadySeeded) return

        runCatching { settings.demoSeeded = true }
            .onFailure {
                Log.e(TAG, "Не удалось сохранить флаг демо-данных", it)
                return
            }

        appScope.launch {
            DemoData.workouts().forEach { workout ->
                runCatching { workouts.save(workout) }
                    .onFailure { Log.e(TAG, "Не удалось сохранить демо-тренировку", it) }
            }
            DemoData.measurements().forEach { measurement ->
                runCatching { bodyMeasurements.save(measurement) }
                    .onFailure { Log.e(TAG, "Не удалось сохранить демо-замер", it) }
            }
        }
    }

    private companion object {
        const val TAG = "GiriBro"
    }
}
