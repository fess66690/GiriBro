package ru.petproject.giribro

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
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
import ru.petproject.giribro.data.BackupFormatException
import ru.petproject.giribro.data.BodyMeasurement
import ru.petproject.giribro.data.BodyMeasurementValidator
import ru.petproject.giribro.data.DiaryBackup
import ru.petproject.giribro.data.DiaryBackupJson
import ru.petproject.giribro.data.Settings
import ru.petproject.giribro.data.StatsPeriod
import ru.petproject.giribro.data.ThemeMode
import ru.petproject.giribro.data.Workout
import ru.petproject.giribro.data.WorkoutValidator
import ru.petproject.giribro.util.pluralRu
import java.io.ByteArrayOutputStream
import java.io.IOException

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

    private val _bodySaving = MutableStateFlow(false)
    val bodySaving: StateFlow<Boolean> = _bodySaving.asStateFlow()

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    private val _pendingImport = MutableStateFlow<DiaryBackup?>(null)
    val pendingImport: StateFlow<DiaryBackup?> = _pendingImport.asStateFlow()

    val workouts: StateFlow<List<Workout>> = app.workouts.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bodyMeasurements: StateFlow<List<BodyMeasurement>> = app.bodyMeasurements.observeAll()
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

    /** Сохраняет замер тела и только после успешной записи вызывает [onSaved]. */
    fun saveBodyMeasurement(measurement: BodyMeasurement, onSaved: () -> Unit = {}) {
        BodyMeasurementValidator.validate(measurement)?.let { problem ->
            showMessage(problem, isError = true)
            return
        }
        if (_bodySaving.value) return

        _bodySaving.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { app.bodyMeasurements.save(measurement) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось сохранить замер", e)
                showMessage("Не удалось сохранить замер. Попробуйте ещё раз.", isError = true)
                return@launch
            } finally {
                _bodySaving.value = false
            }
            showMessage(if (measurement.id == 0L) "Замер сохранён" else "Замер обновлён")
            onSaved()
        }
    }

    fun deleteBodyMeasurement(measurement: BodyMeasurement) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { app.bodyMeasurements.delete(measurement) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось удалить замер", e)
                showMessage("Не удалось удалить замер. Попробуйте ещё раз.", isError = true)
                return@launch
            }
            showMessage("Замер удалён")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    app.database.withTransaction {
                        app.workouts.clear()
                        app.bodyMeasurements.clear()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось очистить дневник", e)
                showMessage("Не удалось очистить дневник. Попробуйте ещё раз.", isError = true)
                return@launch
            }

            runCatching { app.settings.demoSeeded = true }
                .onFailure { Log.e(TAG, "Не удалось сохранить флаг демо-данных", it) }
            showMessage("Дневник и замеры очищены")
        }
    }

    /** Сохраняет дневник и настройки в выбранный пользователем файл. */
    fun exportBackup(uri: Uri) {
        if (_backupBusy.value) return
        _backupBusy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val text = DiaryBackupJson.encode(
                        workouts = app.workouts.all(),
                        bodyMeasurements = app.bodyMeasurements.all(),
                        settings = app.settings.state.value
                    )
                    writeBackupText(uri, text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось сохранить резервную копию", e)
                showMessage("Не удалось сохранить резервную копию. Попробуйте ещё раз.", isError = true)
                return@launch
            } finally {
                _backupBusy.value = false
            }
            showMessage("Резервная копия сохранена")
        }
    }

    /** Читает файл и показывает подтверждение перед заменой дневника. */
    fun loadBackup(uri: Uri) {
        if (_backupBusy.value || _pendingImport.value != null) return
        _backupBusy.value = true
        viewModelScope.launch {
            try {
                val backup = withContext(Dispatchers.IO) {
                    DiaryBackupJson.decode(readBackupText(uri))
                }
                _pendingImport.value = backup
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupFormatException) {
                Log.w(TAG, "Некорректная резервная копия", e)
                showMessage(e.message ?: "Не удалось прочитать резервную копию.", isError = true)
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось прочитать резервную копию", e)
                showMessage("Не удалось прочитать файл резервной копии.", isError = true)
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun cancelImport() {
        if (!_backupBusy.value) _pendingImport.value = null
    }

    /** Заменяет текущий дневник и настройки данными из подтверждённой копии. */
    fun confirmImport() {
        val backup = _pendingImport.value ?: return
        if (_backupBusy.value) return
        _backupBusy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    app.database.withTransaction {
                        app.workouts.replaceAll(backup.workouts)
                        app.bodyMeasurements.replaceAll(backup.bodyMeasurements)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось восстановить резервную копию", e)
                _backupBusy.value = false
                showMessage("Не удалось восстановить дневник. Текущие данные не изменены.", isError = true)
                return@launch
            }

            var settingsError: Throwable? = null
            backup.settings?.let { settings ->
                runCatching { app.settings.apply(settings) }
                    .onFailure { settingsError = it }
            }
            runCatching { app.settings.demoSeeded = true }
                .onFailure { Log.e(TAG, "Не удалось сохранить флаг демо-данных", it) }

            _pendingImport.value = null
            _backupBusy.value = false
            if (settingsError != null) {
                Log.e(TAG, "Дневник восстановлен, но настройки не применились", settingsError)
                showMessage("Дневник восстановлен, но настройки применить не удалось.", isError = true)
            } else {
                val count = backup.workouts.size
                val word = pluralRu(count, "тренировка", "тренировки", "тренировок")
                val bodyCount = backup.bodyMeasurements.size
                val bodyPart = if (bodyCount > 0) {
                    " и $bodyCount " + pluralRu(bodyCount, "замер", "замера", "замеров")
                } else {
                    ""
                }
                showMessage("Восстановлено: $count $word$bodyPart")
            }
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

    private fun readBackupText(uri: Uri): String {
        val input = app.contentResolver.openInputStream(uri)
            ?: throw IOException("Не удалось открыть файл")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > DiaryBackupJson.MAX_FILE_BYTES) {
                    throw BackupFormatException(
                        "Файл больше ${DiaryBackupJson.MAX_FILE_BYTES / (1024 * 1024)} МБ."
                    )
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray().toString(Charsets.UTF_8)
        }
    }

    private fun writeBackupText(uri: Uri, text: String) {
        // Некоторые файловые провайдеры не поддерживают режим "wt".
        val output = runCatching { app.contentResolver.openOutputStream(uri, "wt") }
            .getOrNull()
            ?: app.contentResolver.openOutputStream(uri)
            ?: throw IOException("Не удалось открыть файл для записи")
        output.use { stream ->
            stream.write((text + "\n").toByteArray(Charsets.UTF_8))
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
