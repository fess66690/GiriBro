package ru.petproject.giribro.ui.screens

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.petproject.giribro.data.SetCaptureMode
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
    /** Фактическое время подтверждения замка (в ДЦ короче выбранного порога). */
    val fixationSec: Float = FixationPreset.COMPETITION.seconds,
    val lastFixationSec: Float = 0f,
    val lastRepConfident: Boolean = true,
    val frame: PoseFrame? = null,
    val error: String? = null,
    val captureMode: SetCaptureMode = SetCaptureMode.NONE,
    val setDurationSec: Int = 0,
    val setRemainingSec: Int = 0,
    /**Автоподсчёт можно выключить и считать только вручную.*/
    val autoCount: Boolean = true,
    /**Темп метронома, оборотов в минуту.*/
    val tempoRpm: Int = 15,
    val audioMetronome: Boolean = false,
    val visualMetronome: Boolean = false,
    /**Короткая вспышка на каждом ударе метронома.*/
    val beat: Boolean = false
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
    private var lastBeatIndex = -1L
    private var tone: ToneGenerator? = null

    fun poseAnalyzer(): MediaPipePoseSource? {
        poseSource?.let { return it }
        return runCatching {
            MediaPipePoseSource(
                context = getApplication(),
                onFrame = ::onPoseFrame,
                onError = ::onPoseError
            ).also { poseSource = it }
        }.onFailure { error ->
            reportError("Не удалось создать распознаватель позы", error)
        }.getOrNull()
    }

    fun setExercise(exercise: RepExercise) {
        if (_state.value.exercise == exercise) return
        stop()
        counter = RepCounter(ExerciseProfile.of(exercise, _state.value.preset))
        _state.value = _state.value.copy(
            exercise = exercise,
            fixationSec = counter.profile.effectiveFixationSec,
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
            fixationSec = counter.profile.effectiveFixationSec,
            total = 0,
            left = 0,
            right = 0,
            shortFixations = 0,
            fixationProgress = 0f,
            elapsedMs = 0L,
            activeSides = emptySet()
        )
    }

    fun setAutoCount(enabled: Boolean) {
        if (_state.value.autoCount == enabled) return
        _state.value = _state.value.copy(
            autoCount = enabled,
            fixationProgress = 0f,
            activeSides = emptySet()
        )
    }

    fun setCaptureMode(mode: SetCaptureMode) {
        if (_state.value.captureMode == mode) return
        stop()
        _state.value = _state.value.copy(
            captureMode = mode,
            setDurationSec = 0,
            setRemainingSec = 0
        )
    }

    fun setTempo(rpm: Int) {
        _state.value = _state.value.copy(tempoRpm = rpm.coerceIn(6, 60))
        lastBeatIndex = -1L
    }

    fun setAudioMetronome(enabled: Boolean) {
        _state.value = _state.value.copy(audioMetronome = enabled)
        if (enabled) ensureTone() else releaseTone()
    }

    fun setVisualMetronome(enabled: Boolean) {
        _state.value = _state.value.copy(visualMetronome = enabled)
    }

    /** Автостарт подхода, открытого из таймера. */
    fun startSet(mode: SetCaptureMode, durationSec: Int) {
        _state.value = _state.value.copy(
            captureMode = mode,
            setDurationSec = durationSec.coerceAtLeast(0),
            setRemainingSec = durationSec.coerceAtLeast(0)
        )
        start()
    }

    fun start() {
        if (_state.value.running) return
        counter.reset()
        startedAt = SystemClock.elapsedRealtime()
        lastBeatIndex = -1L
        if (_state.value.audioMetronome) ensureTone()
        _state.value = _state.value.copy(
            running = true,
            total = 0,
            left = 0,
            right = 0,
            shortFixations = 0,
            fixationProgress = 0f,
            elapsedMs = 0L,
            setRemainingSec = _state.value.setDurationSec,
            activeSides = emptySet(),
            lastFixationSec = 0f,
            lastRepConfident = true
        )
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(200)
                if (!_state.value.running) break
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val duration = _state.value.setDurationSec
                val remaining = if (duration > 0) {
                    (duration - elapsed / 1000L).coerceAtLeast(0L).toInt()
                } else {
                    0
                }
                val intervalMs = 60_000L / _state.value.tempoRpm.coerceIn(6, 60)
                val beatIndex = elapsed / intervalMs
                if (beatIndex != lastBeatIndex) {
                    lastBeatIndex = beatIndex
                    if (_state.value.audioMetronome) {
                        runCatching { ensureTone()?.startTone(ToneGenerator.TONE_PROP_BEEP, 60) }
                    }
                }
                _state.value = _state.value.copy(
                    elapsedMs = elapsed,
                    setRemainingSec = remaining,
                    beat = _state.value.visualMetronome && (elapsed % intervalMs) < 200L
                )
                if (duration > 0 && remaining == 0) {
                    stop()
                    break
                }
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
        if (!_state.value.autoCount) {
            // Ручной режим: поза всё ещё рисуется, но повторы не добавляются.
            _state.value = _state.value.copy(
                frame = frame,
                tracking = true,
                fixationProgress = 0f,
                activeSides = emptySet()
            )
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
        reportError("Ошибка распознавания позы", error)
    }

    /** Пишет полный стек в Logcat и оставляет читаемые детали в карточке ошибки. */
    private fun reportError(context: String, error: Throwable) {
        Log.e(TAG, context, error)
        val details = buildString {
            append(error.javaClass.name)
            error.message?.let { append(": ").append(it) }
            var cause = error.cause
            var depth = 0
            while (cause != null && cause !== error && depth < 5) {
                append("\n← ").append(cause.javaClass.name)
                cause.message?.let { append(": ").append(it) }
                cause = cause.cause
                depth++
            }
        }
        _state.value = _state.value.copy(error = "$context\n$details")
    }

    private fun ensureTone(): ToneGenerator? {
        if (tone == null) {
            tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()
        }
        return tone
    }

    private fun releaseTone() {
        runCatching { tone?.release() }
        tone = null
    }

    override fun onCleared() {
        ticker?.cancel()
        releaseTone()
        poseSource?.close()
        poseSource = null
        super.onCleared()
    }

    companion object {
        private const val TAG = "GiriBroCAM"
    }
}
