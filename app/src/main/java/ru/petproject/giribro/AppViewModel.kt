package ru.petproject.giribro

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.petproject.giribro.data.Settings
import ru.petproject.giribro.data.StatsPeriod
import ru.petproject.giribro.data.ThemeMode
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.data.WorkoutValidator

/** Сообщение для пользователя: ошибка операции или подтверждение успеха. */
data class UiMessage(
    val text: String,
    val isError: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GiriBroApp

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    val workouts: StateFlow<List<Workout>> = app.workouts.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<Settings> = app.settings.state

    /**
     * Сохраняет тренировку и только после успешной записи вызывает [onSaved].
     * Так экран не закрывается, если база не приняла данные.
     */
    fun saveWorkout(workout: Workout, onSaved: () -> Unit = {}) {
        WorkoutValidator.validate(workout)?.let { problem ->
            showMessage(problem, isError = true)
            return
        }
        if (_saving.value) return

        _saving.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { app.workouts.save(workout) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось сохранить тренировку", e)
                showMessage("Не удалось сохранить тренировку. Попробуйте ещё раз.", isError = true)
                return@launch
            } finally {
                _saving.value = false
            }
            showMessage("Тренировка сохранена")
            onSaved()
        }
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { app.workouts.delete(workout) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось удалить тренировку", e)
                showMessage("Не удалось удалить тренировку. Попробуйте ещё раз.", isError = true)
                return@launch
            }
            showMessage("Тренировка удалена")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { app.workouts.clear() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось очистить дневник", e)
                showMessage("Не удалось очистить дневник. Попробуйте ещё раз.", isError = true)
                return@launch
            }

            runCatching { app.settings.demoSeeded = true }
                .onFailure { Log.e(TAG, "Не удалось сохранить флаг демо-данных", it) }
            showMessage("Дневник очищен")
        }
    }

    fun setTheme(mode: ThemeMode) {
        updateSetting("Не удалось сохранить тему") { app.settings.setTheme(mode) }
    }

    fun setWeeklyGoal(goal: Int) {
        updateSetting("Не удалось сохранить недельную цель") { app.settings.setWeeklyGoal(goal) }
    }

    fun setDefaultWeight(weight: Int) {
        updateSetting("Не удалось сохранить вес по умолчанию") {
            app.settings.setDefaultWeight(weight)
        }
    }

    fun setStatsPeriod(period: StatsPeriod) {
        updateSetting("Не удалось сохранить период статистики") {
            app.settings.setStatsPeriod(period)
        }
    }

    private inline fun updateSetting(errorText: String, block: () -> Unit) {
        runCatching(block).onFailure { error ->
            Log.e(TAG, errorText, error)
            showMessage("$errorText. Попробуйте ещё раз.", isError = true)
        }
    }

    private fun showMessage(text: String, isError: Boolean = false) {
        _messages.trySend(UiMessage(text, isError))
    }

    private companion object {
        const val TAG = "GiriBro"
    }
}
