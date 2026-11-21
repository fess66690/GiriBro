package ru.petproject.giribro.ui.screens

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
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
import ru.petproject.giribro.GiriBroApp
import ru.petproject.giribro.data.CounterGoal
import ru.petproject.giribro.data.CounterTimerFormat
import ru.petproject.giribro.data.SetCaptureMode
import ru.petproject.giribro.logging.AppLog
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

    /** Цель подхода, выбранная перед стартом. */
    val goal: CounterGoal = CounterGoal.TIME,
    /** Цель по времени, секунды. 0 — без ограничения (подход из таймера). */
    val goalTimeSec: Int = DEFAULT_GOAL_TIME_SEC,
    /** Цель по повторам. */
    val goalReps: Int = DEFAULT_GOAL_REPS,
    /** Вес гири для подсчёта объёма подхода. */
    val weightKg: Int = DEFAULT_WEIGHT_KG,
    /** Формат отсчёта: секундомер или обратный отсчёт. */
    val timerFormat: CounterTimerFormat = CounterTimerFormat.COUNTDOWN,
    /** По умолчанию счёт без записи видео. */
    val captureMode: SetCaptureMode = SetCaptureMode.COUNT,

    val running: Boolean = false,
    /** Цель достигнута и счётчик остановлен автоматически. */
    val finished: Boolean = false,
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
    /** Значение в оверлее: секунды или оставшиеся повторы. */
    val displayValue: Int = DEFAULT_GOAL_TIME_SEC,
    val remainingReps: Int = DEFAULT_GOAL_REPS,
    val frame: PoseFrame? = null,
    val error: String? = null,

    /** Автоподсчёт можно выключить и считать только вручную. */
    val autoCount: Boolean = true,
    /** Темп метронома, оборотов в минуту. */
    val tempoRpm: Int = 15,
    val audioMetronome: Boolean = false,
    val visualMetronome: Boolean = false,
    /** Короткая вспышка на каждом ударе метронома. */
    val beat: Boolean = false
) {
    val elapsedSec: Int get() = (elapsedMs / 1000L).toInt()

    /** При цели «повторы» и обратном отсчёте показываем оставшиеся повторы. */
    val displayAsTime: Boolean
        get() = goal == CounterGoal.TIME || timerFormat == CounterTimerFormat.STOPWATCH

    /** Объём подхода с учётом числа гирь и коэффициента упражнения. */
    val volumeKg: Double
        get() = weightKg.toDouble() * total * exercise.sectionType.coefficient

    val rpm: Int
        get() = if (elapsedSec > 0) {
            (total * 60.0 / elapsedSec).roundToInt()
        } else {
            0
        }

    companion object {
        const val DEFAULT_GOAL_TIME_SEC = 60
        const val DEFAULT_GOAL_REPS = 20
        const val DEFAULT_WEIGHT_KG = 24
        const val MAX_GOAL_TIME_SEC = 2 * 60 * 60
        const val MAX_GOAL_REPS = 1000
        const val MIN_WEIGHT_KG = 4
        const val MAX_WEIGHT_KG = 48
    }
}

class CameraRepViewModel(application: Application) : AndroidViewModel(application) {

    private var counter = RepCounter(
        ExerciseProfile.of(RepExercise.JERK, FixationPreset.COMPETITION)
    )
    private val _state = MutableStateFlow(CameraRepUiState())
    val state: StateFlow<CameraRepUiState> = _state.asStateFlow()

    private var poseSource: MediaPipePoseSource? = null
    private var ticker: Job? = null
    private var startedAt = 0L
    private var lastEvent: RepEvent? = null
    private var lastBeatIndex = -1L
    private var tone: ToneGenerator? = null

    init {
        // Вес и порог фиксации берём из настроек приложения как значения по умолчанию.
        val settings = runCatching { (getApplication() as GiriBroApp).settings.state.value }
            .getOrNull()
        val preset = settings?.fixationPreset ?: FixationPreset.COMPETITION
        counter = RepCounter(ExerciseProfile.of(RepExercise.JERK, preset))
        _state.value = CameraRepUiState(
            preset = preset,
            fixationSec = counter.profile.effectiveFixationSec,
            weightKg = settings?.defaultWeight ?: CameraRepUiState.DEFAULT_WEIGHT_KG
        )
    }

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

    // --- Настройки подхода: все применяются кнопкой «Старт». ---

    fun setGoal(goal: CounterGoal) {
        if (_state.value.goal == goal) return
        reconfigure { it.copy(goal = goal) }
    }

    fun setGoalTimeSec(seconds: Int) {
        val value = seconds.coerceIn(1, CameraRepUiState.MAX_GOAL_TIME_SEC)
        if (_state.value.goalTimeSec == value) return
        reconfigure { it.copy(goalTimeSec = value) }
    }

    fun setGoalReps(reps: Int) {
        val value = reps.coerceIn(1, CameraRepUiState.MAX_GOAL_REPS)
        if (_state.value.goalReps == value) return
        reconfigure { it.copy(goalReps = value) }
    }

    fun setWeightKg(weightKg: Int) {
        val value = weightKg.coerceIn(
            CameraRepUiState.MIN_WEIGHT_KG,
            CameraRepUiState.MAX_WEIGHT_KG
        )
        _state.value = _state.value.copy(weightKg = value)
    }

    fun setTimerFormat(format: CounterTimerFormat) {
        if (_state.value.timerFormat == format) return
        reconfigure { it.copy(timerFormat = format) }
    }

    fun setCaptureMode(mode: SetCaptureMode) {
        if (_state.value.captureMode == mode) return
        reconfigure { it.copy(captureMode = mode) }
    }

    fun setExercise(exercise: RepExercise) {
        if (_state.value.exercise == exercise) return
        counter = RepCounter(ExerciseProfile.of(exercise, _state.value.preset))
        reconfigure {
            it.copy(exercise = exercise, fixationSec = counter.profile.effectiveFixationSec)
        }
    }

    fun setPreset(preset: FixationPreset) {
        if (_state.value.preset == preset) return
        counter = RepCounter(ExerciseProfile.of(_state.value.exercise, preset))
        reconfigure {
            it.copy(preset = preset, fixationSec = counter.profile.effectiveFixationSec)
        }
    }

    fun setAutoCount(enabled: Boolean) {
        if (_state.value.autoCount == enabled) return
        _state.value = _state.value.copy(
            autoCount = enabled,
            fixationProgress = 0f,
            activeSides = emptySet()
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

    /** Автостарт подхода, открытого из таймера: цель — заданное время. */
    fun startSet(mode: SetCaptureMode, durationSec: Int) {
        _state.value = _state.value.copy(
            goal = CounterGoal.TIME,
            goalTimeSec = durationSec.coerceAtLeast(0),
            timerFormat = CounterTimerFormat.COUNTDOWN,
            captureMode = mode
        )
        start()
    }

    fun start() {
        if (_state.value.running) return
        counter.reset()
        startedAt = SystemClock.elapsedRealtime()
        lastBeatIndex = -1L
        if (_state.value.audioMetronome) ensureTone()
        _state.value = freshCounts(_state.value).copy(running = true)
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(200)
                if (!_state.value.running) break
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val elapsedSec = (elapsed / 1000L).toInt()
                val current = _state.value
                val intervalMs = 60_000L / current.tempoRpm.coerceIn(6, 60)
                val beatIndex = elapsed / intervalMs
                if (beatIndex != lastBeatIndex) {
                    lastBeatIndex = beatIndex
                    if (current.audioMetronome) {
                        runCatching { ensureTone()?.startTone(ToneGenerator.TONE_PROP_BEEP, 60) }
                    }
                }
                val reached = goalReached(current, elapsedSec, current.total)
                _state.value = current.copy(
                    elapsedMs = elapsed,
                    displayValue = displayValueFor(current, elapsedSec, current.total),
                    remainingReps = (current.goalReps - current.total).coerceAtLeast(0),
                    beat = current.visualMetronome && (elapsed % intervalMs) < 200L
                )
                if (reached) {
                    finish()
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
        _state.value = freshCounts(_state.value).copy(error = null)
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
        val current = _state.value
        val elapsedSec = current.elapsedSec
        val reached = goalReached(current, elapsedSec, snapshot.total)
        _state.value = current.copy(
            total = snapshot.total,
            left = snapshot.left,
            right = snapshot.right,
            shortFixations = snapshot.shortFixations,
            fixationProgress = snapshot.fixationProgress,
            tracking = snapshot.tracking,
            activeSides = snapshot.activeSides,
            lastFixationSec = snapshot.lastFixationSec,
            lastRepConfident = snapshot.lastRepConfident,
            remainingReps = (current.goalReps - snapshot.total).coerceAtLeast(0),
            displayValue = displayValueFor(current, elapsedSec, snapshot.total)
        )
        if (current.running && reached) finish()
    }

    private fun onPoseError(error: Throwable) {
        reportError("Ошибка распознавания позы", error)
    }

    /** Пишет полный стек в Logcat и оставляет читаемые детали в карточке ошибки. */
    private fun reportError(context: String, error: Throwable) {
        AppLog.e(TAG, context, error)
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

    /** Сброс результатов перед стартом или при изменении настроек. */
    private fun freshCounts(state: CameraRepUiState): CameraRepUiState = state.copy(
        total = 0,
        left = 0,
        right = 0,
        shortFixations = 0,
        fixationProgress = 0f,
        elapsedMs = 0L,
        activeSides = emptySet(),
        lastFixationSec = 0f,
        lastRepConfident = true,
        finished = false,
        beat = false,
        remainingReps = state.goalReps,
        displayValue = displayValueFor(state, 0, 0)
    )

    /** Изменение настроек останавливает подход и обнуляет его результаты. */
    private fun reconfigure(change: (CameraRepUiState) -> CameraRepUiState) {
        stop()
        _state.value = freshCounts(change(_state.value))
    }

    /** Что показывать в оверлее: время или оставшиеся повторы. */
    private fun displayValueFor(state: CameraRepUiState, elapsedSec: Int, total: Int): Int =
        when {
            state.goal == CounterGoal.REPS &&
                state.timerFormat == CounterTimerFormat.COUNTDOWN ->
                (state.goalReps - total).coerceAtLeast(0)

            state.timerFormat == CounterTimerFormat.STOPWATCH || state.goalTimeSec <= 0 ->
                elapsedSec.coerceAtLeast(0)

            else -> (state.goalTimeSec - elapsedSec).coerceAtLeast(0)
        }

    private fun goalReached(state: CameraRepUiState, elapsedSec: Int, total: Int): Boolean =
        when (state.goal) {
            CounterGoal.TIME -> state.goalTimeSec > 0 && elapsedSec >= state.goalTimeSec
            CounterGoal.REPS -> state.goalReps > 0 && total >= state.goalReps
        }

    private fun finish() {
        ticker?.cancel()
        ticker = null
        val current = _state.value
        _state.value = current.copy(
            running = false,
            finished = true,
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
            beat = false
        )
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
