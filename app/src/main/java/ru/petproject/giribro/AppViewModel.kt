package ru.petproject.giribro

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.petproject.giribro.data.Settings
import ru.petproject.giribro.data.StatsPeriod
import ru.petproject.giribro.data.ThemeMode
import ru.petproject.giribro.data.Workout

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GiriBroApp

    val workouts: StateFlow<List<Workout>> = app.workouts.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<Settings> = app.settings.state

    fun saveWorkout(workout: Workout) {
        viewModelScope.launch(Dispatchers.IO) { app.workouts.save(workout) }
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch(Dispatchers.IO) { app.workouts.delete(workout) }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            app.workouts.clear()
            app.settings.demoSeeded = true
        }
    }

    fun setTheme(mode: ThemeMode) = app.settings.setTheme(mode)

    fun setWeeklyGoal(goal: Int) = app.settings.setWeeklyGoal(goal)

    fun setDefaultWeight(weight: Int) = app.settings.setDefaultWeight(weight)

    fun setStatsPeriod(period: StatsPeriod) = app.settings.setStatsPeriod(period)
}
