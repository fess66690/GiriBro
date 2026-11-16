package ru.petproject.giribro.ui.screens

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.petproject.giribro.vision.ExerciseProfile
import ru.petproject.giribro.vision.FixationPreset
import ru.petproject.giribro.vision.MediaPipePoseSource
import ru.petproject.giribro.vision.PoseFrame
import ru.petproject.giribro.vision.RepCounter
import ru.petproject.giribro.vision.RepExercise
import ru.petproject.giribro.vision.RepEvent
import ru.petproject.giribro.vision.RepSnapshot
import ru.petproject.giribro.vision.Side
import kotlin.math.roundToInt

data class CameraRepUiState(
    val exercise: RepExercise = RepExercise.JERK,
    val preset: FixationPreset = FixationPreset.COMPETITION,
    val running: Boolean = false,
    val total: Int = 0,
    val left: Int = 0,
    val right: Int = 0,
    val shortFixations: Int = 0,
    val fixationProgress: Float = 0f,
    val tracking: Boolean = false,
    val activeSides: Set<Side> = emptySet(),
    val elapsedMs: Long = 0L,
    val lastFixationSec: Float = 0f,
    val lastRepConfident: Boolean = true,
    val frame: PoseFrame? = null,
    val error: String? = null
) {
    val elapsedSec: Int get() = (elapsedMs / 1000L).toInt()
    val rpm: Int
        get() = if (elapsedSec > 0) {
            (total * 60.0 / elapsedSec).roundToInt()
        } else {
            0
        }
}

class CameraRepViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(CameraRepUiState())
    val state: StateFlow<CameraRepUiState> = _state.asStateFlow()

    private var counter = RepCounter(ExerciseProfile.of(RepExercise.JERK, FixationPreset.COMPETITION))
    private var poseSource: MediaPipePoseSource? = null
    private var ticker: Job? = null
    private var startedAt = 0L
    private var lastEvent: RepEvent? = null

    fun poseAnalyzer(): MediaPipePoseSource? {
        poseSource?.let { return it }
        return runCatching {
            MediaPipePoseSource(
                context = getApplication(),
                onFrame = ::onPoseFrame,
                onError = ::onPoseError
            ).also { poseSource = it }
        }.onFailure { error ->
            _state.value = _state.value.copy(
                error = error.message ?: "Не удалось запустить распознавание позы"
            )
        }.getOrNull()
    }

    fun setExercise(exercise: RepExercise) {
        if (_state.value.exercise == exercise) return
        stop()
        counter = RepCounter(ExerciseProfile.of(exercise, _state.value.preset))
        _state.value = _state.value.copy(
            exercise = exercise,
            total = 0,
            left = 0,
            right = 0,
            shortFixations = 0,
            fixationProgress = 0f,
            elapsedMs = 0L,
            activeSides = emptySet()
        )
    }

    fun setPreset(preset: FixationPreset) {
        if (_state.value.preset == preset) return
        stop()
        counter = RepCounter(ExerciseProfile.of(_state.value.exercise, preset))
        _state.value = _state.value.copy(
            preset = preset,
            total = 0,
            left = 0,
            right = 0,
            shortFixations = 0,
            fixationProgress = 0f,
            elapsedMs = 0L,
            activeSides = emptySet()
        )
    }

    fun start() {
        if (_state.value.running) return
        counter.reset()
        startedAt = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(
            running = true,
            total = 0,
            left = 0,
            right = 0,
            shortFixations = 0,
            fixationProgress = 0f,
            elapsedMs = 0L,
            activeSides = emptySet(),
            lastFixationSec = 0f,
            lastRepConfident = true
        )
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(200)
                if (!_state.value.running) break
                _state.value = _state.value.copy(
                    elapsedMs = SystemClock.elapsedRealtime() - startedAt
                )
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        if (_state.value.running) {
            _state.value = _state.value.copy(
                running = false,
                elapsedMs = SystemClock.elapsedRealtime() - startedAt
            )
        }
    }

    fun reset() {
        stop()
        counter.reset()
        _state.value = _state.value.copy(
            total = 0,
            left = 0,
            right = 0,
            shortFixations = 0,
            fixationProgress = 0f,
            elapsedMs = 0L,
            activeSides = emptySet(),
            lastFixationSec = 0f,
            lastRepConfident = true,
            error = null
        )
    }

    fun addRep() {
        counter.manualAdd()
        applySnapshot(counter.currentSnapshot())
    }

    fun removeRep() {
        counter.manualRemove()
        applySnapshot(counter.currentSnapshot())
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun onPoseFrame(frame: PoseFrame) {
        if (!frame.hasPose) {
            _state.value = _state.value.copy(
                frame = if (frame.landmarks.isEmpty()) null else frame,
                tracking = false,
                fixationProgress = 0f,
                activeSides = emptySet()
            )
            return
        }
        if (!_state.value.running) {
            _state.value = _state.value.copy(frame = frame, tracking = true)
            return
        }
        val update = counter.process(frame)
        lastEvent = update.events.lastOrNull()
        _state.value = _state.value.copy(frame = frame)
        applySnapshot(update.snapshot)
    }

    private fun applySnapshot(snapshot: RepSnapshot) {
        _state.value = _state.value.copy(
            total = snapshot.total,
            left = snapshot.left,
            right = snapshot.right,
            shortFixations = snapshot.shortFixations,
            fixationProgress = snapshot.fixationProgress,
            tracking = snapshot.tracking,
            activeSides = snapshot.activeSides,
            lastFixationSec = snapshot.lastFixationSec,
            lastRepConfident = snapshot.lastRepConfident
        )
    }

    private fun onPoseError(error: Throwable) {
        _state.value = _state.value.copy(
            error = error.message ?: "Ошибка распознавания позы"
        )
    }

    override fun onCleared() {
        ticker?.cancel()
        poseSource?.close()
        poseSource = null
        super.onCleared()
    }
}
