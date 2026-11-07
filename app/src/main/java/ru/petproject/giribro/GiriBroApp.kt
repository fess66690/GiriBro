package ru.petproject.giribro

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.petproject.giribro.data.AppDatabase
import ru.petproject.giribro.data.DemoData
import ru.petproject.giribro.data.SettingsRepository
import ru.petproject.giribro.data.WorkoutRepository
import ru.petproject.giribro.timer.TimerNotifications

class GiriBroApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val workouts: WorkoutRepository by lazy { WorkoutRepository(database.workoutDao()) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        TimerNotifications.ensureChannel(this)
        seedDemoIfNeeded()
    }

    private fun seedDemoIfNeeded() {
        if (settings.demoSeeded) return
        settings.demoSeeded = true
        appScope.launch {
            DemoData.workouts().forEach { workouts.save(it) }
        }
    }
}
